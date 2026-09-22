package com.wallet.exception;

/** Thrown when a row is missing. Maps to HTTP 404. */
public class ResourceNotFoundException extends RuntimeException {
    /** Creates the exception. */
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
