package com.wallet.service;

import com.wallet.dao.LedgerDAO;
import com.wallet.dao.TransactionDAO;
import com.wallet.dao.WalletDAO;
import com.wallet.dto.TransferRequest;
import com.wallet.dto.TransferResponse;
import com.wallet.exception.InsufficientBalanceException;
import com.wallet.exception.ResourceNotFoundException;
import com.wallet.exception.WalletFrozenException;
import com.wallet.model.LedgerEntry;
import com.wallet.model.Transaction;
import com.wallet.model.Wallet;
import com.wallet.util.IdempotencyKeyGenerator;
import com.wallet.util.TxManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.Optional;

/**
 * CORE service: concurrency-safe, atomic, idempotent P2P transfers.
 *
 * <p>Correctness strategy (defence in depth):
 * <ol>
 *   <li>Single DB transaction owned by {@link TxManager}.</li>
 *   <li>{@code SELECT ... FOR UPDATE} on both wallets serialises
 *       concurrent transfers on the same rows (row-level locking).</li>
 *   <li>Balance re-checked AFTER the lock (no TOCTOU).</li>
 *   <li>Idempotency key checked in code AND enforced by the UNIQUE
 *       constraint in the DB (last line of defence against races).</li>
 *   <li>{@code CHECK (balance >= 0)} in DDL guards even buggy code.</li>
 *   <li>Two ledger rows per transfer give an auditable statement.</li>
 * </ol>
 */
@Service
public class TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferService.class);

    private final DataSource dataSource;
    private final TxManager txManager;
    private final WalletDAO walletDAO;
    private final TransactionDAO transactionDAO;
    private final LedgerDAO ledgerDAO;

    /** Wires dependencies (tests construct this directly with an H2 pool). */
    public TransferService(DataSource dataSource, TxManager txManager,
                           WalletDAO walletDAO, TransactionDAO transactionDAO, LedgerDAO ledgerDAO) {
        this.dataSource = dataSource;
        this.txManager = txManager;
        this.walletDAO = walletDAO;
        this.transactionDAO = transactionDAO;
        this.ledgerDAO = ledgerDAO;
    }

    /**
     * Executes an atomic P2P transfer.
     *
     * @param req transfer payload
     * @return response with txn id and replay flag
     */
    public TransferResponse transfer(TransferRequest req) {
        if (req.getAmount() == null || req.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be > 0");
        }
        if (req.getFromWalletId() == null || req.getToWalletId() == null) {
            throw new IllegalArgumentException("Both wallet ids are required");
        }
        if (req.getFromWalletId().equals(req.getToWalletId())) {
            throw new IllegalArgumentException("Source and destination wallets must differ");
        }
        String key = IdempotencyKeyGenerator.resolve(req.getIdempotencyKey());

        return txManager.execute(conn -> {
            try {
                // 1. Idempotency: a SUCCESS txn with the same key is replayed, never duplicated.
                Optional<Transaction> existing = transactionDAO.findByIdempotencyKey(conn, key);
                if (existing.isPresent() && "SUCCESS".equals(existing.get().getStatus())) {
                    Transaction t = existing.get();
                    log.info("Idempotent replay key={} txn={}", key, t.getTxnId());
                    return new TransferResponse(t.getTxnId(), t.getStatus(), t.getAmount(), true);
                }

                // 2. BEGIN (handled by TxManager) + row-level locks.
                //    Lock ordering by wallet id avoids deadlocks when A->B and B->A race.
                long first = Math.min(req.getFromWalletId(), req.getToWalletId());
                long second = Math.max(req.getFromWalletId(), req.getToWalletId());
                walletDAO.findByIdForUpdate(conn, first);
                walletDAO.findByIdForUpdate(conn, second);

                Wallet from = walletDAO.findByIdForUpdate(conn, req.getFromWalletId())
                        .orElseThrow(() -> new ResourceNotFoundException("Source wallet not found"));
                Wallet to = walletDAO.findByIdForUpdate(conn, req.getToWalletId())
                        .orElseThrow(() -> new ResourceNotFoundException("Destination wallet not found"));

                // 3. Validate AFTER locking (balances cannot change under us now).
                if (!"ACTIVE".equals(from.getStatus())) {
                    throw new WalletFrozenException("Source wallet is " + from.getStatus());
                }
                if (!"ACTIVE".equals(to.getStatus())) {
                    throw new WalletFrozenException("Destination wallet is " + to.getStatus());
                }
                if (from.getBalance().compareTo(req.getAmount()) < 0) {
                    throw new InsufficientBalanceException(
                            "Insufficient balance: have " + from.getBalance() + ", need " + req.getAmount());
                }

                // 4. Move money.
                walletDAO.adjustBalance(conn, from.getWalletId(), req.getAmount().negate());
                walletDAO.adjustBalance(conn, to.getWalletId(), req.getAmount());

                // 5. Record the transaction (UNIQUE key is the race backstop).
                Transaction tx = new Transaction();
                tx.setFromWallet(from.getWalletId());
                tx.setToWallet(to.getWalletId());
                tx.setAmount(req.getAmount());
                tx.setType("P2P");
                tx.setStatus("SUCCESS");
                tx.setIdempotencyKey(key);
                tx.setRemarks(req.getRemarks());
                long txnId;
                try {
                    txnId = transactionDAO.insert(conn, tx);
                } catch (SQLIntegrityConstraintViolationException dup) {
                    // Another thread committed the same key first: replay it.
                    Transaction winner = transactionDAO.findByIdempotencyKey(conn, key)
                            .orElseThrow(() -> new RuntimeException("Idempotency conflict"));
                    return new TransferResponse(winner.getTxnId(), winner.getStatus(), winner.getAmount(), true);
                }

                // 6. Append the two ledger lines with post-tx balances.
                Wallet fromAfter = walletDAO.findById(conn, from.getWalletId()).orElseThrow();
                Wallet toAfter = walletDAO.findById(conn, to.getWalletId()).orElseThrow();

                LedgerEntry debit = new LedgerEntry();
                debit.setTxnId(txnId);
                debit.setWalletId(from.getWalletId());
                debit.setEntryType("DEBIT");
                debit.setAmount(req.getAmount());
                debit.setBalanceAfter(fromAfter.getBalance());
                ledgerDAO.insert(conn, debit);

                LedgerEntry credit = new LedgerEntry();
                credit.setTxnId(txnId);
                credit.setWalletId(to.getWalletId());
                credit.setEntryType("CREDIT");
                credit.setAmount(req.getAmount());
                credit.setBalanceAfter(toAfter.getBalance());
                ledgerDAO.insert(conn, credit);

                log.info("Transfer {} -> {} amount={} txn={}",
                        from.getWalletId(), to.getWalletId(), req.getAmount(), txnId);
                // 7. COMMIT (handled by TxManager).
                return new TransferResponse(txnId, "SUCCESS", req.getAmount(), false);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Fetches a transaction by id.
     *
     * @param txnId id
     * @return transaction
     */
    public Transaction get(long txnId) {
        try (Connection conn = dataSource.getConnection()) {
            return transactionDAO.findById(conn, txnId)
                    .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + txnId));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
