package com.wallet.util;

import java.util.UUID;

/**
 * Generates idempotency keys (UUID v4) when the client omits
 * the X-Idempotency-Key header.
 */
public final class IdempotencyKeyGenerator {

    private IdempotencyKeyGenerator() {
    }

    /** Returns a new random key. */
    public static String generate() {
        return UUID.randomUUID().toString();
    }

    /**
     * Returns the client key if present, else a fresh server-side key.
     *
     * @param clientKey value of X-Idempotency-Key header (nullable)
     * @return effective key
     */
    public static String resolve(String clientKey) {
        if (clientKey != null && !clientKey.isBlank()) {
            return clientKey.trim();
        }
        return generate();
    }
}
