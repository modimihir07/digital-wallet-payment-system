package com.wallet.dao;

import com.wallet.model.Transaction;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.Optional;

/**
 * Raw-JDBC access for {@code transactions}.
 */
@Repository
public class TransactionDAO {

    /**
     * Inserts a transaction row.
     *
     * @param conn connection
     * @param tx   transaction (id populated)
     * @return generated txn id
     * @throws SQLException on DB error (duplicate idempotency key -> SQLIntegrityConstraintViolation)
     */
    public long insert(Connection conn, Transaction tx) throws SQLException {
        String sql = "INSERT INTO transactions (from_wallet, to_wallet, amount, type, status, idempotency_key, remarks)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            if (tx.getFromWallet() == null) {
                ps.setNull(1, Types.BIGINT);
            } else {
                ps.setLong(1, tx.getFromWallet());
            }
            if (tx.getToWallet() == null) {
                ps.setNull(2, Types.BIGINT);
            } else {
                ps.setLong(2, tx.getToWallet());
            }
            ps.setBigDecimal(3, tx.getAmount());
            ps.setString(4, tx.getType());
            ps.setString(5, tx.getStatus());
            ps.setString(6, tx.getIdempotencyKey());
            ps.setString(7, tx.getRemarks());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    long id = rs.getLong(1);
                    tx.setTxnId(id);
                    return id;
                }
            }
        }
        throw new SQLException("Failed to insert transaction");
    }

    /**
     * Finds a transaction by id.
     *
     * @param conn  connection
     * @param txnId id
     * @return txn if present
     * @throws SQLException on DB error
     */
    public Optional<Transaction> findById(Connection conn, long txnId) throws SQLException {
        String sql = "SELECT txn_id, from_wallet, to_wallet, amount, type, status, idempotency_key, remarks, created_at"
                + " FROM transactions WHERE txn_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, txnId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(map(rs));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Finds a transaction by idempotency key.
     *
     * @param conn connection
     * @param key  key
     * @return txn if present
     * @throws SQLException on DB error
     */
    public Optional<Transaction> findByIdempotencyKey(Connection conn, String key) throws SQLException {
        if (key == null) {
            return Optional.empty();
        }
        String sql = "SELECT txn_id, from_wallet, to_wallet, amount, type, status, idempotency_key, remarks, created_at"
                + " FROM transactions WHERE idempotency_key = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(map(rs));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Updates transaction status.
     *
     * @param conn   connection
     * @param txnId  id
     * @param status new status
     * @throws SQLException on DB error
     */
    public void updateStatus(Connection conn, long txnId, String status) throws SQLException {
        String sql = "UPDATE transactions SET status = ? WHERE txn_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setLong(2, txnId);
            ps.executeUpdate();
        }
    }

    /**
     * Counts ledger-backed transactions (used by idempotency test).
     *
     * @param conn connection
     * @param key  idempotency key
     * @return count
     * @throws SQLException on DB error
     */
    public int countByIdempotencyKey(Connection conn, String key) throws SQLException {
        String sql = "SELECT COUNT(*) FROM transactions WHERE idempotency_key = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    /**
     * Maps the current row to a Transaction.
     *
     * @param rs result set
     * @return transaction
     * @throws SQLException on DB error
     */
    public Transaction map(ResultSet rs) throws SQLException {
        Transaction t = new Transaction();
        t.setTxnId(rs.getLong("txn_id"));
        long fw = rs.getLong("from_wallet");
        t.setFromWallet(rs.wasNull() ? null : fw);
        long tw = rs.getLong("to_wallet");
        t.setToWallet(rs.wasNull() ? null : tw);
        t.setAmount(rs.getBigDecimal("amount"));
        t.setType(rs.getString("type"));
        t.setStatus(rs.getString("status"));
        t.setIdempotencyKey(rs.getString("idempotency_key"));
        t.setRemarks(rs.getString("remarks"));
        java.sql.Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) {
            t.setCreatedAt(ts.toInstant());
        }
        return t;
    }
}
