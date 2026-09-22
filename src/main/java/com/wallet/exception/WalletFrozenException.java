package com.wallet.exception;

/** Thrown when a wallet is FROZEN/CLOSED. Maps to HTTP 403. */
public class WalletFrozenException extends RuntimeException {
    /** Creates the exception. */
    public WalletFrozenException(String message) {
        super(message);
    }
}
