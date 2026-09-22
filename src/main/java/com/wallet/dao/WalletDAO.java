package com.wallet.dao;

import com.wallet.model.Wallet;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Raw-JDBC access for {@code wallets}, including {@code FOR UPDATE} row locks.
 */
@Repository
public class WalletDAO {

    /**
     * Creates a wallet for a user.
     *
     * @param conn   connection
     * @param userId owner
     * @return generated wallet id
     * @throws SQLException on DB error
     */
    public long create(Connection conn, long userId) throws SQLException {
        String sql = "INSERT INTO wallets (user_id, balance, currency, status) VALUES (?, 0.00, 'INR', 'ACTIVE')";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, userId);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        throw new SQLException("Failed to create wallet");
    }

    /**
     * Finds a wallet by id (no lock).
     *
     * @param conn     connection
     * @param walletId id
     * @return wallet if present
     * @throws SQLException on DB error
     */
    public Optional<Wallet> findById(Connection conn, long walletId) throws SQLException {
        String sql = "SELECT wallet_id, user_id, balance, currency, status, created_at FROM wallets WHERE wallet_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, walletId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(map(rs));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Finds a wallet by id with a row-level write lock.
     * THIS is the concurrency-safety primitive: blocks concurrent
     * transfers on the same wallet until the tx commits/rolls back.
     *
     * @param conn     connection (must be inside a tx)
     * @param walletId id
     * @return wallet if present
     * @throws SQLException on DB error
     */
    public Optional<Wallet> findByIdForUpdate(Connection conn, long walletId) throws SQLException {
        String sql = "SELECT wallet_id, user_id, balance, currency, status, created_at FROM wallets WHERE wallet_id = ? FOR UPDATE";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, walletId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(map(rs));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Finds the wallet owned by a user.
     *
     * @param conn   connection
     * @param userId owner
     * @return wallet if present
     * @throws SQLException on DB error
     */
    public Optional<Wallet> findByUserId(Connection conn, long userId) throws SQLException {
        String sql = "SELECT wallet_id, user_id, balance, currency, status, created_at FROM wallets WHERE user_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(map(rs));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Adjusts a balance by a delta (positive or negative).
     *
     * @param conn     connection
     * @param walletId id
     * @param delta    signed amount
     * @throws SQLException on DB error (incl. CHECK constraint violation)
     */
    public void adjustBalance(Connection conn, long walletId, java.math.BigDecimal delta) throws SQLException {
        String sql = "UPDATE wallets SET balance = balance + ? WHERE wallet_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBigDecimal(1, delta);
            ps.setLong(2, walletId);
            int n = ps.executeUpdate();
            if (n != 1) {
                throw new SQLException("Wallet not found: " + walletId);
            }
        }
    }

    /**
     * Sets wallet status (ACTIVE/FROZEN/CLOSED).
     *
     * @param conn     connection
     * @param walletId id
     * @param status   new status
     * @throws SQLException on DB error
     */
    public void setStatus(Connection conn, long walletId, String status) throws SQLException {
        String sql = "UPDATE wallets SET status = ? WHERE wallet_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setLong(2, walletId);
            ps.executeUpdate();
        }
    }

    /**
     * Lists wallets with pagination (admin).
     *
     * @param conn   connection
     * @param limit  page size
     * @param offset offset
     * @return wallets
     * @throws SQLException on DB error
     */
    public List<Wallet> list(Connection conn, int limit, int offset) throws SQLException {
        String sql = "SELECT wallet_id, user_id, balance, currency, status, created_at FROM wallets ORDER BY wallet_id LIMIT ? OFFSET ?";
        List<Wallet> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            ps.setInt(2, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(map(rs));
                }
            }
        }
        return out;
    }

    /**
     * Maps the current row to a Wallet.
     *
     * @param rs result set
     * @return wallet
     * @throws SQLException on DB error
     */
    public Wallet map(ResultSet rs) throws SQLException {
        Wallet w = new Wallet();
        w.setWalletId(rs.getLong("wallet_id"));
        w.setUserId(rs.getLong("user_id"));
        w.setBalance(rs.getBigDecimal("balance"));
        w.setCurrency(rs.getString("currency"));
        w.setStatus(rs.getString("status"));
        java.sql.Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) {
            w.setCreatedAt(ts.toInstant());
        }
        return w;
    }
}
