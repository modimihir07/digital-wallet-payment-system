package com.wallet.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Customer bill payable to a merchant. */
public class Bill {
    private long billId;
    private long userId;
    private long merchantId;
    private BigDecimal amount;
    private String status;
    private LocalDate dueDate;
    private Long paidTxnId;
    private Instant createdAt;
    // Joined display fields (optional)
    private String merchantName;
    private String userName;

    public long getBillId() { return billId; }
    public void setBillId(long billId) { this.billId = billId; }
    public long getUserId() { return userId; }
    public void setUserId(long userId) { this.userId = userId; }
    public long getMerchantId() { return merchantId; }
    public void setMerchantId(long merchantId) { this.merchantId = merchantId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public Long getPaidTxnId() { return paidTxnId; }
    public void setPaidTxnId(Long paidTxnId) { this.paidTxnId = paidTxnId; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public String getMerchantName() { return merchantName; }
    public void setMerchantName(String merchantName) { this.merchantName = merchantName; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
}
