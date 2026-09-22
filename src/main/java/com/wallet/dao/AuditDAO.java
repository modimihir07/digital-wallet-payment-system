package com.wallet.dao;

import com.wallet.model.AuditLog;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only access to trigger-written {@code audit_log}.
 */
@Repository
public class AuditDAO {

    /**
     * Lists audit rows newest-first with pagination.
     *
     * @param conn   connection
     * @param limit  page size
     * @param offset offset
     * @return rows
     * @throws SQLException on DB error
     */
    public List<AuditLog> list(Connection conn, int limit, int offset) throws SQLException {
        String sql = "SELECT log_id, table_name, action, record_id, old_value, new_value, changed_by, changed_at"
                + " FROM audit_log ORDER BY changed_at DESC, log_id DESC LIMIT ? OFFSET ?";
        List<AuditLog> out = new ArrayList<>();
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
     * Maps the current row to an AuditLog.
     *
     * @param rs result set
     * @return log
     * @throws SQLException on DB error
     */
    public AuditLog map(ResultSet rs) throws SQLException {
        AuditLog a = new AuditLog();
        a.setLogId(rs.getLong("log_id"));
        a.setTableName(rs.getString("table_name"));
        a.setAction(rs.getString("action"));
        long rec = rs.getLong("record_id");
        a.setRecordId(rs.wasNull() ? null : rec);
        a.setOldValue(rs.getString("old_value"));
        a.setNewValue(rs.getString("new_value"));
        long by = rs.getLong("changed_by");
        a.setChangedBy(rs.wasNull() ? null : by);
        java.sql.Timestamp ts = rs.getTimestamp("changed_at");
        if (ts != null) {
            a.setChangedAt(ts.toInstant());
        }
        return a;
    }
}
