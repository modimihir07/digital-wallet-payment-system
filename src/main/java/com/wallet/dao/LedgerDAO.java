package com.wallet.dao;

import com.wallet.model.LedgerEntry;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Raw-JDBC access for append-only {@code ledger_entries}.
 */
@Repository
public class LedgerDAO {

    /**
     * Appends a ledger line.
     *
     * @param conn  connection
     * @param entry entry (id populated)
     * @throws SQLException on DB error
     */
    public void insert(Connection conn, LedgerEntry entry) throws SQLException {
        String sql = "INSERT INTO ledger_entries (txn_id, wallet_id, entry_type, amount, balance_after)"
                + " VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, entry.getTxnId());
            ps.setLong(2, entry.getWalletId());
            ps.setString(3, entry.getEntryType());
            ps.setBigDecimal(4, entry.getAmount());
            ps.setBigDecimal(5, entry.getBalanceAfter());
            ps.executeUpdate();
        }
    }

    /**
     * Returns a wallet statement page (newest first).
     *
     * @param conn     connection
     * @param walletId wallet
     * @param limit    page size
     * @param offset   offset
     * @return entries
     * @throws SQLException on DB error
     */
    public List<LedgerEntry> statement(Connection conn, long walletId, int limit, int offset) throws SQLException {
        String sql = "SELECT entry_id, txn_id, wallet_id, entry_type, amount, balance_after, created_at"
                + " FROM ledger_entries WHERE wallet_id = ? ORDER BY created_at DESC, entry_id DESC LIMIT ? OFFSET ?";
        List<LedgerEntry> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, walletId);
            ps.setInt(2, limit);
            ps.setInt(3, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(map(rs));
                }
            }
        }
        return out;
    }

    /**
     * Counts ledger lines for a wallet.
     *
     * @param conn     connection
     * @param walletId wallet
     * @return count
     * @throws SQLException on DB error
     */
    public int countByWallet(Connection conn, long walletId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM ledger_entries WHERE wallet_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, walletId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    /**
     * Counts ledger lines for a transaction.
     *
     * @param conn  connection
     * @param txnId transaction
     * @return count
     * @throws SQLException on DB error
     */
    public int countByTxn(Connection conn, long txnId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM ledger_entries WHERE txn_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, txnId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    /**
     * Maps the current row to a LedgerEntry.
     *
     * @param rs result set
     * @return entry
     * @throws SQLException on DB error
     */
    public LedgerEntry map(ResultSet rs) throws SQLException {
        LedgerEntry e = new LedgerEntry();
        e.setEntryId(rs.getLong("entry_id"));
        e.setTxnId(rs.getLong("txn_id"));
        e.setWalletId(rs.getLong("wallet_id"));
        e.setEntryType(rs.getString("entry_type"));
        e.setAmount(rs.getBigDecimal("amount"));
        e.setBalanceAfter(rs.getBigDecimal("balance_after"));
        java.sql.Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) {
            e.setCreatedAt(ts.toInstant());
        }
        return e;
    }
}
