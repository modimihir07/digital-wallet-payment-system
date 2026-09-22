package com.wallet.dao;

import com.wallet.model.User;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Raw-JDBC CRUD for {@code users}. No business logic; every method
 * takes the caller's transaction {@link Connection}.
 */
@Repository
public class UserDAO {

    /**
     * Inserts a user.
     *
     * @param conn open transaction connection
     * @param user user to insert (id populated)
     * @return generated user id
     * @throws SQLException on DB error
     */
    public long insert(Connection conn, User user) throws SQLException {
        String sql = "INSERT INTO users (name, email, phone, password_hash, status) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, user.getName());
            ps.setString(2, user.getEmail());
            ps.setString(3, user.getPhone());
            ps.setString(4, user.getPasswordHash());
            ps.setString(5, user.getStatus() == null ? "ACTIVE" : user.getStatus());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    long id = rs.getLong(1);
                    user.setUserId(id);
                    return id;
                }
            }
        }
        throw new SQLException("Failed to insert user, no key returned");
    }

    /**
     * Finds a user by email.
     *
     * @param conn  connection
     * @param email email
     * @return user if present
     * @throws SQLException on DB error
     */
    public Optional<User> findByEmail(Connection conn, String email) throws SQLException {
        String sql = "SELECT user_id, name, email, phone, password_hash, status, created_at FROM users WHERE email = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(map(rs));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Finds a user by id.
     *
     * @param conn   connection
     * @param userId id
     * @return user if present
     * @throws SQLException on DB error
     */
    public Optional<User> findById(Connection conn, long userId) throws SQLException {
        String sql = "SELECT user_id, name, email, phone, password_hash, status, created_at FROM users WHERE user_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(map(rs));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Assigns a role to a user (idempotent).
     *
     * @param conn     connection
     * @param userId   user
     * @param roleName role name
     * @throws SQLException on DB error
     */
    public void assignRole(Connection conn, long userId, String roleName) throws SQLException {
        String sql = "INSERT IGNORE INTO user_roles (user_id, role_id) "
                + "SELECT ?, role_id FROM roles WHERE name = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setString(2, roleName);
            ps.executeUpdate();
        }
    }

    /**
     * Lists role names for a user.
     *
     * @param conn   connection
     * @param userId user
     * @return role names
     * @throws SQLException on DB error
     */
    public List<String> rolesOf(Connection conn, long userId) throws SQLException {
        String sql = "SELECT r.name FROM roles r JOIN user_roles ur ON ur.role_id = r.role_id WHERE ur.user_id = ?";
        List<String> roles = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    roles.add(rs.getString(1));
                }
            }
        }
        return roles;
    }

    /**
     * Maps the current row to a User.
     *
     * @param rs result set
     * @return user
     * @throws SQLException on DB error
     */
    public User map(ResultSet rs) throws SQLException {
        User u = new User();
        u.setUserId(rs.getLong("user_id"));
        u.setName(rs.getString("name"));
        u.setEmail(rs.getString("email"));
        u.setPhone(rs.getString("phone"));
        u.setPasswordHash(rs.getString("password_hash"));
        u.setStatus(rs.getString("status"));
        java.sql.Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) {
            u.setCreatedAt(ts.toInstant());
        }
        return u;
    }
}
