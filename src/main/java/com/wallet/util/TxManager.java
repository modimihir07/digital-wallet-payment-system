package com.wallet.util;

import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Owns transaction boundaries. Services call {@link #execute} with a lambda;
 * DAOs only run SQL on the provided connection.
 */
@Component
public class TxManager {

    private final DataSource dataSource;

    /** Creates the manager over the Hikari pool. */
    public TxManager(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Callback executed inside a single DB transaction. */
    @FunctionalInterface
    public interface ConnectionCallback<T> {
        /**
         * Runs work on an open transaction connection.
         *
         * @param conn active connection with autoCommit=false
         * @return result
         * @throws Exception any failure triggers rollback
         */
        T doInConnection(Connection conn) throws Exception;
    }

    /**
     * Begins a transaction, runs the callback, commits on success,
     * rolls back on any exception.
     *
     * @param callback unit of work
     * @param <T>      return type
     * @return callback result
     * @throws RuntimeException wrapping the original failure
     */
    public <T> T execute(ConnectionCallback<T> callback) {
        try (Connection conn = dataSource.getConnection()) {
            boolean prevAuto = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                T result = callback.doInConnection(conn);
                conn.commit();
                return result;
            } catch (Exception e) {
                try {
                    conn.rollback();
                } catch (SQLException rb) {
                    e.addSuppressed(rb);
                }
                if (e instanceof RuntimeException re) {
                    throw re;
                }
                throw new RuntimeException(e);
            } finally {
                conn.setAutoCommit(prevAuto);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to obtain connection", e);
        }
    }
}
