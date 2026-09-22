package com.wallet.model;

import java.math.BigDecimal;
import java.time.Instant;

/** Wallet owned 1:1 by a user. Money stored as BigDecimal. */
public class Wallet {
    private long walletId;
    private long userId;
    private BigDecimal balance;
    private String currency;
    private String status;
    private Instant createdAt;

    public long getWalletId() { return walletId; }
    public void setWalletId(long walletId) { this.walletId = walletId; }
    public long getUserId() { return userId; }
    public void setUserId(long userId) { this.userId = userId; }
    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
