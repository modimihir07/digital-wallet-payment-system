package com.wallet.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

/**
 * Creates and validates JWTs (HS256, 24h default validity).
 */
@Component
public class JwtUtil {

    private final SecretKey key;
    private final long expiryMs;

    /**
     * Builds the signer from config.
     *
     * @param secret   HMAC secret (min 32 bytes; longer recommended)
     * @param expiryMs token validity in millis
     */
    public JwtUtil(
            @Value("${jwt.secret:change-me-to-a-long-random-secret-at-least-256-bits-long-please}") String secret,
            @Value("${jwt.expiry:86400000}") long expiryMs) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            // Pad short demo secrets to 32 bytes so Keys.hmacShaKeyFor is happy.
            byte[] padded = new byte[32];
            System.arraycopy(bytes, 0, padded, 0, bytes.length);
            bytes = padded;
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.expiryMs = expiryMs;
    }

    /**
     * Issues a token carrying user id, email and roles.
     *
     * @param userId user id
     * @param email  login email
     * @param roles  role names
     * @return compact JWT
     */
    public String generateToken(long userId, String email, List<String> roles) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("email", email)
                .claim("roles", roles)
                .issuedAt(new Date(now))
                .expiration(new Date(now + expiryMs))
                .signWith(key)
                .compact();
    }

    /**
     * Parses and verifies a token.
     *
     * @param token compact JWT
     * @return claims
     */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Extracts the user id (subject).
     *
     * @param token compact JWT
     * @return user id
     */
    public long userId(String token) {
        return Long.parseLong(parse(token).getSubject());
    }

    /**
     * Checks whether the token carries ROLE_ADMIN.
     *
     * @param token compact JWT
     * @return true if admin
     */
    @SuppressWarnings("unchecked")
    public boolean isAdmin(String token) {
        Object roles = parse(token).get("roles");
        if (roles instanceof List<?> list) {
            return list.contains("ADMIN") || list.contains("ROLE_ADMIN");
        }
        return false;
    }
}
