package com.wallet.dto;

import java.math.BigDecimal;

/** Transfer result. */
public class TransferResponse {
    private long txnId;
    private String status;
    private BigDecimal amount;
    private boolean idempotentReplay;

    public TransferResponse() {
    }

    /** Builds a transfer response. */
    public TransferResponse(long txnId, String status, BigDecimal amount, boolean idempotentReplay) {
        this.txnId = txnId;
        this.status = status;
        this.amount = amount;
        this.idempotentReplay = idempotentReplay;
    }

    public long getTxnId() { return txnId; }
    public void setTxnId(long txnId) { this.txnId = txnId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public boolean isIdempotentReplay() { return idempotentReplay; }
    public void setIdempotentReplay(boolean idempotentReplay) { this.idempotentReplay = idempotentReplay; }
}
