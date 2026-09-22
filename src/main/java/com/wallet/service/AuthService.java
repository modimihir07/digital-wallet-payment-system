package com.wallet.service;

import com.wallet.dao.UserDAO;
import com.wallet.dao.WalletDAO;
import com.wallet.dto.AuthResponse;
import com.wallet.dto.LoginRequest;
import com.wallet.dto.RegisterRequest;
import com.wallet.exception.ResourceNotFoundException;
import com.wallet.model.User;
import com.wallet.security.JwtUtil;
import com.wallet.security.PasswordUtil;
import com.wallet.util.TxManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.List;

/**
 * Registration + login. Creates user, wallet and USER role atomically.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final DataSource dataSource;
    private final TxManager txManager;
    private final UserDAO userDAO;
    private final WalletDAO walletDAO;
    private final JwtUtil jwtUtil;

    /** Wires dependencies. */
    public AuthService(DataSource dataSource, TxManager txManager,
                       UserDAO userDAO, WalletDAO walletDAO, JwtUtil jwtUtil) {
        this.dataSource = dataSource;
        this.txManager = txManager;
        this.userDAO = userDAO;
        this.walletDAO = walletDAO;
        this.jwtUtil = jwtUtil;
    }

    /**
     * Registers a user, auto-creates a wallet and assigns USER role.
     *
     * @param req registration payload
     * @return auth response with JWT
     */
    public AuthResponse register(RegisterRequest req) {
        return txManager.execute(conn -> {
            User existing = userDAO.findByEmail(conn, req.getEmail()).orElse(null);
            if (existing != null) {
                throw new IllegalArgumentException("Email already registered");
            }
            User u = new User();
            u.setName(req.getName());
            u.setEmail(req.getEmail());
            u.setPhone(req.getPhone());
            u.setPasswordHash(PasswordUtil.hash(req.getPassword()));
            u.setStatus("ACTIVE");
            try {
                long userId = userDAO.insert(conn, u);
                walletDAO.create(conn, userId);
                userDAO.assignRole(conn, userId, "USER");
                List<String> roles = userDAO.rolesOf(conn, userId);
                String token = jwtUtil.generateToken(userId, req.getEmail(), roles);
                log.info("Registered user {}", req.getEmail());
                return new AuthResponse(token, userId, req.getEmail(), roles);
            } catch (SQLIntegrityConstraintViolationException dup) {
                throw new IllegalArgumentException("Email or phone already registered");
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Validates credentials and issues a JWT.
     *
     * @param req login payload
     * @return auth response with JWT
     */
    public AuthResponse login(LoginRequest req) {
        try (Connection conn = dataSource.getConnection()) {
            User u = userDAO.findByEmail(conn, req.getEmail())
                    .orElseThrow(() -> new ResourceNotFoundException("Invalid email or password"));
            if (!"ACTIVE".equals(u.getStatus())) {
                throw new IllegalArgumentException("User is blocked");
            }
            if (!PasswordUtil.verify(req.getPassword(), u.getPasswordHash())) {
                throw new ResourceNotFoundException("Invalid email or password");
            }
            List<String> roles = userDAO.rolesOf(conn, u.getUserId());
            String token = jwtUtil.generateToken(u.getUserId(), u.getEmail(), roles);
            log.info("Login success {}", req.getEmail());
            return new AuthResponse(token, u.getUserId(), u.getEmail(), roles);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
