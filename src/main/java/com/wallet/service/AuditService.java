package com.wallet.service;

import com.wallet.dao.AuditDAO;
import com.wallet.dao.WalletDAO;
import com.wallet.exception.ResourceNotFoundException;
import com.wallet.model.AuditLog;
import com.wallet.model.Wallet;
import com.wallet.util.TxManager;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin operations: wallet freeze/unfreeze, audit reads, reports over views.
 */
@Service
public class AuditService {

    private final DataSource dataSource;
    private final TxManager txManager;
    private final AuditDAO auditDAO;
    private final WalletDAO walletDAO;

    /** Wires dependencies. */
    public AuditService(DataSource dataSource, TxManager txManager,
                        AuditDAO auditDAO, WalletDAO walletDAO) {
        this.dataSource = dataSource;
        this.txManager = txManager;
        this.auditDAO = auditDAO;
        this.walletDAO = walletDAO;
    }

    /**
     * Lists wallets (admin).
     *
     * @param page zero-based page
     * @param size page size
     * @return wallets
     */
    public List<Wallet> listWallets(int page, int size) {
        try (Connection conn = dataSource.getConnection()) {
            return walletDAO.list(conn, size, page * size);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Freezes a wallet.
     *
     * @param walletId id
     */
    public void freeze(long walletId) {
        txManager.execute(conn -> {
            try {
                Wallet w = walletDAO.findById(conn, walletId)
                        .orElseThrow(() -> new ResourceNotFoundException("Wallet not found: " + walletId));
                if (w.getWalletId() == 0) {
                    throw new ResourceNotFoundException("Wallet not found");
                }
                walletDAO.setStatus(conn, walletId, "FROZEN");
                return null;
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Unfreezes a wallet back to ACTIVE.
     *
     * @param walletId id
     */
    public void unfreeze(long walletId) {
        txManager.execute(conn -> {
            try {
                walletDAO.findById(conn, walletId)
                        .orElseThrow(() -> new ResourceNotFoundException("Wallet not found: " + walletId));
                walletDAO.setStatus(conn, walletId, "ACTIVE");
                return null;
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Reads audit rows (admin).
     *
     * @param page zero-based page
     * @param size page size
     * @return rows
     */
    public List<AuditLog> audit(int page, int size) {
        try (Connection conn = dataSource.getConnection()) {
            return auditDAO.list(conn, size, page * size);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Reads the v_daily_summary view for one date.
     *
     * @param date YYYY-MM-DD
     * @return summary map (empty values when no rows)
     */
    public Map<String, Object> dailyReport(String date) {
        String sql = "SELECT txn_date, total_txns, total_volume, success_count, fail_count"
                + " FROM v_daily_summary WHERE txn_date = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, date);
            try (ResultSet rs = ps.executeQuery()) {
                Map<String, Object> m = new LinkedHashMap<>();
                if (rs.next()) {
                    m.put("date", String.valueOf(rs.getDate(1)));
                    m.put("totalTxns", rs.getLong(2));
                    m.put("totalVolume", rs.getBigDecimal(3));
                    m.put("successCount", rs.getLong(4));
                    m.put("failCount", rs.getLong(5));
                } else {
                    m.put("date", date);
                    m.put("totalTxns", 0);
                    m.put("totalVolume", 0);
                    m.put("successCount", 0);
                    m.put("failCount", 0);
                }
                return m;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Reads the v_top_users view.
     *
     * @return ranked users
     */
    public List<Map<String, Object>> topUsers() {
        String sql = "SELECT user_id, user_name, email, total_outgoing, txn_count FROM v_top_users";
        List<Map<String, Object>> out = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("userId", rs.getLong(1));
                m.put("userName", rs.getString(2));
                m.put("email", rs.getString(3));
                m.put("totalOutgoing", rs.getBigDecimal(4));
                m.put("txnCount", rs.getLong(5));
                out.add(m);
            }
            return out;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
