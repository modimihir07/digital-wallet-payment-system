package com.wallet.model;

import java.math.BigDecimal;
import java.time.Instant;

/** Append-only ledger line (DEBIT or CREDIT) with balance snapshot. */
public class LedgerEntry {
    private long entryId;
    private long txnId;
    private long walletId;
    private String entryType;
    private BigDecimal amount;
    private BigDecimal balanceAfter;
    private Instant createdAt;

    public long getEntryId() { return entryId; }
    public void setEntryId(long entryId) { this.entryId = entryId; }
    public long getTxnId() { return txnId; }
    public void setTxnId(long txnId) { this.txnId = txnId; }
    public long getWalletId() { return walletId; }
    public void setWalletId(long walletId) { this.walletId = walletId; }
    public String getEntryType() { return entryType; }
    public void setEntryType(String entryType) { this.entryType = entryType; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public BigDecimal getBalanceAfter() { return balanceAfter; }
    public void setBalanceAfter(BigDecimal balanceAfter) { this.balanceAfter = balanceAfter; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
