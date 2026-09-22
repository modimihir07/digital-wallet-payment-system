package com.wallet.service;

import com.wallet.dao.LedgerDAO;
import com.wallet.dao.TransactionDAO;
import com.wallet.dao.WalletDAO;
import com.wallet.dto.TopUpRequest;
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
import java.util.List;
import java.util.Optional;

/**
 * Wallet reads, simulated top-ups and statements.
 */
@Service
public class WalletService {

    private static final Logger log = LoggerFactory.getLogger(WalletService.class);

    private final DataSource dataSource;
    private final TxManager txManager;
    private final WalletDAO walletDAO;
    private final TransactionDAO transactionDAO;
    private final LedgerDAO ledgerDAO;

    /** Wires dependencies. */
    public WalletService(DataSource dataSource, TxManager txManager,
                         WalletDAO walletDAO, TransactionDAO transactionDAO, LedgerDAO ledgerDAO) {
        this.dataSource = dataSource;
        this.txManager = txManager;
        this.walletDAO = walletDAO;
        this.transactionDAO = transactionDAO;
        this.ledgerDAO = ledgerDAO;
    }

    /**
     * Returns the caller's wallet.
     *
     * @param userId owner
     * @return wallet
     */
    public Wallet myWallet(long userId) {
        try (Connection conn = dataSource.getConnection()) {
            return walletDAO.findByUserId(conn, userId)
                    .orElseThrow(() -> new ResourceNotFoundException("Wallet not found"));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns a statement page (ledger entries newest-first).
     *
     * @param userId owner
     * @param page   zero-based page
     * @param size   page size
     * @return entries
     */
    public List<LedgerEntry> statement(long userId, int page, int size) {
        try (Connection conn = dataSource.getConnection()) {
            Wallet w = walletDAO.findByUserId(conn, userId)
                    .orElseThrow(() -> new ResourceNotFoundException("Wallet not found"));
            return ledgerDAO.statement(conn, w.getWalletId(), size, page * size);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Simulates adding money: idempotent, row-locked, ledger-backed.
     *
     * @param userId owner
     * @param req    amount + optional key
     * @return top-up txn id
     */
    public long topUp(long userId, TopUpRequest req) {
        if (req.getAmount() == null || req.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be > 0");
        }
        String key = IdempotencyKeyGenerator.resolve(req.getIdempotencyKey());
        return txManager.execute(conn -> {
            try {
                Wallet w = walletDAO.findByUserId(conn, userId)
                        .orElseThrow(() -> new ResourceNotFoundException("Wallet not found"));
                // Idempotency first: replay returns the original txn.
                Optional<Transaction> replay = transactionDAO.findByIdempotencyKey(conn, key);
                if (replay.isPresent()) {
                    return replay.get().getTxnId();
                }
                // Lock the wallet row.
                Wallet locked = walletDAO.findByIdForUpdate(conn, w.getWalletId())
                        .orElseThrow(() -> new ResourceNotFoundException("Wallet not found"));
                if (!"ACTIVE".equals(locked.getStatus())) {
                    throw new WalletFrozenException("Wallet is not ACTIVE");
                }
                Transaction tx = new Transaction();
                tx.setFromWallet(null);
                tx.setToWallet(locked.getWalletId());
                tx.setAmount(req.getAmount());
                tx.setType("TOPUP");
                tx.setStatus("SUCCESS");
                tx.setIdempotencyKey(key);
                tx.setRemarks("Simulated top-up");
                long txnId;
                try {
                    txnId = transactionDAO.insert(conn, tx);
                } catch (SQLIntegrityConstraintViolationException dup) {
                    // Lost a race with an identical key: return the winner.
                    Transaction winner = transactionDAO.findByIdempotencyKey(conn, key)
                            .orElseThrow(() -> new RuntimeException("Idempotency conflict"));
                    return winner.getTxnId();
                }
                walletDAO.adjustBalance(conn, locked.getWalletId(), req.getAmount());
                Wallet after = walletDAO.findById(conn, locked.getWalletId()).orElseThrow();
                LedgerEntry e = new LedgerEntry();
                e.setTxnId(txnId);
                e.setWalletId(locked.getWalletId());
                e.setEntryType("CREDIT");
                e.setAmount(req.getAmount());
                e.setBalanceAfter(after.getBalance());
                ledgerDAO.insert(conn, e);
                log.info("Top-up wallet={} amount={}", locked.getWalletId(), req.getAmount());
                return txnId;
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
