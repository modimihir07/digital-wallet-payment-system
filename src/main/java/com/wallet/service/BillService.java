package com.wallet.service;

import com.wallet.dao.BillDAO;
import com.wallet.dao.LedgerDAO;
import com.wallet.dao.TransactionDAO;
import com.wallet.dao.WalletDAO;
import com.wallet.exception.InsufficientBalanceException;
import com.wallet.exception.ResourceNotFoundException;
import com.wallet.exception.WalletFrozenException;
import com.wallet.model.Bill;
import com.wallet.model.LedgerEntry;
import com.wallet.model.Transaction;
import com.wallet.model.Wallet;
import com.wallet.util.IdempotencyKeyGenerator;
import com.wallet.util.TxManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.List;
import java.util.Optional;

/**
 * Bill listing + atomic bill payment (wallet -> merchant wallet).
 */
@Service
public class BillService {

    private static final Logger log = LoggerFactory.getLogger(BillService.class);

    private final DataSource dataSource;
    private final TxManager txManager;
    private final BillDAO billDAO;
    private final WalletDAO walletDAO;
    private final TransactionDAO transactionDAO;
    private final LedgerDAO ledgerDAO;

    /** Wires dependencies. */
    public BillService(DataSource dataSource, TxManager txManager, BillDAO billDAO,
                       WalletDAO walletDAO, TransactionDAO transactionDAO, LedgerDAO ledgerDAO) {
        this.dataSource = dataSource;
        this.txManager = txManager;
        this.billDAO = billDAO;
        this.walletDAO = walletDAO;
        this.transactionDAO = transactionDAO;
        this.ledgerDAO = ledgerDAO;
    }

    /**
     * Lists the caller's bills.
     *
     * @param userId owner
     * @return bills
     */
    public List<Bill> myBills(long userId) {
        try (Connection conn = dataSource.getConnection()) {
            return billDAO.listByUser(conn, userId);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Pays an UNPAID bill atomically: locks bill + payer wallet, moves money,
     * writes txn + ledger rows, marks bill PAID (trigger writes audit_log).
     *
     * @param userId owner (must own the bill)
     * @param billId bill
     * @param clientKey optional X-Idempotency-Key
     * @return paid txn id
     */
    public long payBill(long userId, long billId, String clientKey) {
        String key = IdempotencyKeyGenerator.resolve(clientKey);
        return txManager.execute(conn -> {
            try {
                Optional<Transaction> replay = transactionDAO.findByIdempotencyKey(conn, key);
                if (replay.isPresent()) {
                    return replay.get().getTxnId();
                }
                Bill bill = billDAO.findByIdForUpdate(conn, billId)
                        .orElseThrow(() -> new ResourceNotFoundException("Bill not found: " + billId));
                if (bill.getUserId() != userId) {
                    throw new IllegalArgumentException("Bill does not belong to this user");
                }
                if ("PAID".equals(bill.getStatus())) {
                    throw new IllegalArgumentException("Bill already paid");
                }
                Wallet payer = walletDAO.findByUserId(conn, userId)
                        .orElseThrow(() -> new ResourceNotFoundException("Wallet not found"));
                Wallet locked = walletDAO.findByIdForUpdate(conn, payer.getWalletId())
                        .orElseThrow(() -> new ResourceNotFoundException("Wallet not found"));
                if (!"ACTIVE".equals(locked.getStatus())) {
                    throw new WalletFrozenException("Wallet is " + locked.getStatus());
                }
                if (locked.getBalance().compareTo(bill.getAmount()) < 0) {
                    throw new InsufficientBalanceException("Insufficient balance to pay bill");
                }
                Long merchantWallet = billDAO.merchantWalletId(conn, bill.getMerchantId());
                if (merchantWallet != null) {
                    walletDAO.findByIdForUpdate(conn, merchantWallet);
                }

                Transaction tx = new Transaction();
                tx.setFromWallet(locked.getWalletId());
                tx.setToWallet(merchantWallet);
                tx.setAmount(bill.getAmount());
                tx.setType("BILL");
                tx.setStatus("SUCCESS");
                tx.setIdempotencyKey(key);
                tx.setRemarks("Bill payment #" + billId);
                long txnId;
                try {
                    txnId = transactionDAO.insert(conn, tx);
                } catch (SQLIntegrityConstraintViolationException dup) {
                    Transaction winner = transactionDAO.findByIdempotencyKey(conn, key)
                            .orElseThrow(() -> new RuntimeException("Idempotency conflict"));
                    return winner.getTxnId();
                }

                walletDAO.adjustBalance(conn, locked.getWalletId(), bill.getAmount().negate());
                Wallet after = walletDAO.findById(conn, locked.getWalletId()).orElseThrow();
                LedgerEntry debit = new LedgerEntry();
                debit.setTxnId(txnId);
                debit.setWalletId(locked.getWalletId());
                debit.setEntryType("DEBIT");
                debit.setAmount(bill.getAmount());
                debit.setBalanceAfter(after.getBalance());
                ledgerDAO.insert(conn, debit);

                if (merchantWallet != null) {
                    walletDAO.adjustBalance(conn, merchantWallet, bill.getAmount());
                    Wallet mAfter = walletDAO.findById(conn, merchantWallet).orElseThrow();
                    LedgerEntry credit = new LedgerEntry();
                    credit.setTxnId(txnId);
                    credit.setWalletId(merchantWallet);
                    credit.setEntryType("CREDIT");
                    credit.setAmount(bill.getAmount());
                    credit.setBalanceAfter(mAfter.getBalance());
                    ledgerDAO.insert(conn, credit);
                }

                billDAO.markPaid(conn, billId, txnId);
                log.info("Bill {} paid, txn={}", billId, txnId);
                return txnId;
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
