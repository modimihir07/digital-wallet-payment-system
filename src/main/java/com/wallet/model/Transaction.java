package com.wallet.model;

import java.math.BigDecimal;
import java.time.Instant;

/** Money movement header row. */
public class Transaction {
    private long txnId;
    private Long fromWallet;
    private Long toWallet;
    private BigDecimal amount;
    private String type;
    private String status;
    private String idempotencyKey;
    private String remarks;
    private Instant createdAt;

    public long getTxnId() { return txnId; }
    public void setTxnId(long txnId) { this.txnId = txnId; }
    public Long getFromWallet() { return fromWallet; }
    public void setFromWallet(Long fromWallet) { this.fromWallet = fromWallet; }
    public Long getToWallet() { return toWallet; }
    public void setToWallet(Long toWallet) { this.toWallet = toWallet; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
