package com.wallet.exception;

/** Thrown on idempotency-key reuse with conflicting payload. Maps to HTTP 409. */
public class DuplicateTransactionException extends RuntimeException {
    /** Creates the exception. */
    public DuplicateTransactionException(String message) {
        super(message);
    }
}
