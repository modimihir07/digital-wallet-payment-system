package com.wallet.exception;

/** Thrown when the source wallet lacks funds. Maps to HTTP 400. */
public class InsufficientBalanceException extends RuntimeException {
    /** Creates the exception. */
    public InsufficientBalanceException(String message) {
        super(message);
    }
}
