package com.wallet.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;

/**
 * Validates {@code Authorization: Bearer <jwt>} on protected routes.
 * Skips {@code /api/auth/**}. Stores userId/email/roles as request attributes.
 * Admin-only routes (prefix {@code /api/admin/}) require ROLE_ADMIN.
 */
public class JwtAuthFilter implements Filter {

    /** Request attribute holding the authenticated user id. */
    public static final String ATTR_USER_ID = "authUserId";
    /** Request attribute holding the authenticated email. */
    public static final String ATTR_EMAIL = "authEmail";
    /** Request attribute holding role names. */
    public static final String ATTR_ROLES = "authRoles";

    private final JwtUtil jwtUtil;

    /** Creates the filter. */
    public JwtAuthFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;
        String path = request.getRequestURI();

        if (path.startsWith("/api/auth/") || path.equals("/error")) {
            chain.doFilter(req, res);
            return;
        }

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            unauthorized(response, "Missing or invalid Authorization header");
            return;
        }
        String token = header.substring(7);
        try {
            Claims claims = jwtUtil.parse(token);
            long userId = Long.parseLong(claims.getSubject());
            String email = claims.get("email", String.class);
            List<String> roles = (List<String>) claims.get("roles");
            request.setAttribute(ATTR_USER_ID, userId);
            request.setAttribute(ATTR_EMAIL, email);
            request.setAttribute(ATTR_ROLES, roles);

            if (path.startsWith("/api/admin/")) {
                boolean admin = roles != null && (roles.contains("ADMIN") || roles.contains("ROLE_ADMIN"));
                if (!admin) {
                    forbidden(response, "Admin role required");
                    return;
                }
            }
            chain.doFilter(req, res);
        } catch (JwtException | IllegalArgumentException e) {
            unauthorized(response, "Invalid or expired token");
        }
    }

    private static void unauthorized(HttpServletResponse res, String msg) throws IOException {
        res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        res.setContentType("application/json");
        res.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"" + msg + "\"}");
    }

    private static void forbidden(HttpServletResponse res, String msg) throws IOException {
        res.setStatus(HttpServletResponse.SC_FORBIDDEN);
        res.setContentType("application/json");
        res.getWriter().write("{\"error\":\"Forbidden\",\"message\":\"" + msg + "\"}");
    }
}
