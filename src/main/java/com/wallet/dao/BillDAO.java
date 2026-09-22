package com.wallet.dao;

import com.wallet.model.Bill;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Raw-JDBC access for {@code bills} and {@code merchants}.
 */
@Repository
public class BillDAO {

    /**
     * Finds a bill by id with a write lock (for pay flow).
     *
     * @param conn   connection
     * @param billId id
     * @return bill if present
     * @throws SQLException on DB error
     */
    public Optional<Bill> findByIdForUpdate(Connection conn, long billId) throws SQLException {
        String sql = "SELECT bill_id, user_id, merchant_id, amount, status, due_date, paid_txn_id, created_at"
                + " FROM bills WHERE bill_id = ? FOR UPDATE";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, billId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(map(rs));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Finds a bill by id (no lock).
     *
     * @param conn   connection
     * @param billId id
     * @return bill if present
     * @throws SQLException on DB error
     */
    public Optional<Bill> findById(Connection conn, long billId) throws SQLException {
        String sql = "SELECT bill_id, user_id, merchant_id, amount, status, due_date, paid_txn_id, created_at"
                + " FROM bills WHERE bill_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, billId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(map(rs));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Lists a user's bills (newest first).
     *
     * @param conn   connection
     * @param userId owner
     * @return bills
     * @throws SQLException on DB error
     */
    public List<Bill> listByUser(Connection conn, long userId) throws SQLException {
        String sql = "SELECT b.bill_id, b.user_id, b.merchant_id, b.amount, b.status, b.due_date,"
                + " b.paid_txn_id, b.created_at, m.name AS merchant_name"
                + " FROM bills b JOIN merchants m ON m.merchant_id = b.merchant_id"
                + " WHERE b.user_id = ? ORDER BY b.created_at DESC";
        List<Bill> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Bill b = map(rs);
                    try {
                        b.setMerchantName(rs.getString("merchant_name"));
                    } catch (SQLException ignored) {
                        // column may be absent in some queries
                    }
                    out.add(b);
                }
            }
        }
        return out;
    }

    /**
     * Marks a bill PAID.
     *
     * @param conn      connection
     * @param billId    id
     * @param paidTxnId successful txn
     * @throws SQLException on DB error
     */
    public void markPaid(Connection conn, long billId, long paidTxnId) throws SQLException {
        String sql = "UPDATE bills SET status = 'PAID', paid_txn_id = ? WHERE bill_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, paidTxnId);
            ps.setLong(2, billId);
            ps.executeUpdate();
        }
    }

    /**
     * Returns the merchant wallet id (nullable).
     *
     * @param conn       connection
     * @param merchantId merchant
     * @return wallet id or null
     * @throws SQLException on DB error
     */
    public Long merchantWalletId(Connection conn, long merchantId) throws SQLException {
        String sql = "SELECT wallet_id FROM merchants WHERE merchant_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, merchantId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long id = rs.getLong(1);
                    return rs.wasNull() ? null : id;
                }
            }
        }
        return null;
    }

    /**
     * Maps the current row to a Bill.
     *
     * @param rs result set
     * @return bill
     * @throws SQLException on DB error
     */
    public Bill map(ResultSet rs) throws SQLException {
        Bill b = new Bill();
        b.setBillId(rs.getLong("bill_id"));
        b.setUserId(rs.getLong("user_id"));
        b.setMerchantId(rs.getLong("merchant_id"));
        b.setAmount(rs.getBigDecimal("amount"));
        b.setStatus(rs.getString("status"));
        Date d = rs.getDate("due_date");
        if (d != null) {
            b.setDueDate(d.toLocalDate());
        }
        long txn = rs.getLong("paid_txn_id");
        b.setPaidTxnId(rs.wasNull() ? null : txn);
        java.sql.Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) {
            b.setCreatedAt(ts.toInstant());
        }
        return b;
    }

    /** Silences unused-import warnings for Types on minimal drivers. */
    @SuppressWarnings("unused")
    private static int nullableBigint() {
        return Types.BIGINT;
    }
}
