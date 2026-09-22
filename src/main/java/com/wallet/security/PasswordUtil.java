package com.wallet.security;

import org.mindrot.jbcrypt.BCrypt;

/**
 * BCrypt password hashing helper (cost factor 10).
 */
public final class PasswordUtil {

    private PasswordUtil() {
    }

    /**
     * Hashes a raw password.
     *
     * @param raw plain password
     * @return BCrypt hash
     */
    public static String hash(String raw) {
        return BCrypt.hashpw(raw, BCrypt.gensalt(10));
    }

    /**
     * Verifies a raw password against a stored hash.
     *
     * @param raw  plain password
     * @param hash stored BCrypt hash
     * @return true on match
     */
    public static boolean verify(String raw, String hash) {
        if (raw == null || hash == null) {
            return false;
        }
        return BCrypt.checkpw(raw, hash);
    }
}
