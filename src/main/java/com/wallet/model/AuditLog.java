package com.wallet.model;

import java.time.Instant;

/** DB-trigger-written audit row. */
public class AuditLog {
    private long logId;
    private String tableName;
    private String action;
    private Long recordId;
    private String oldValue;
    private String newValue;
    private Long changedBy;
    private Instant changedAt;

    public long getLogId() { return logId; }
    public void setLogId(long logId) { this.logId = logId; }
    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public Long getRecordId() { return recordId; }
    public void setRecordId(Long recordId) { this.recordId = recordId; }
    public String getOldValue() { return oldValue; }
    public void setOldValue(String oldValue) { this.oldValue = oldValue; }
    public String getNewValue() { return newValue; }
    public void setNewValue(String newValue) { this.newValue = newValue; }
    public Long getChangedBy() { return changedBy; }
    public void setChangedBy(Long changedBy) { this.changedBy = changedBy; }
    public Instant getChangedAt() { return changedAt; }
    public void setChangedAt(Instant changedAt) { this.changedAt = changedAt; }
}
