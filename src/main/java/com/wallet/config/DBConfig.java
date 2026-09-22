package com.wallet.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Small helper exposing the pooled {@link DataSource} and validating
 * connectivity at startup. Transaction demarcation lives in TxManager.
 */
@Component
public class DBConfig {

    private static final Logger log = LoggerFactory.getLogger(DBConfig.class);
    private final DataSource dataSource;

    /** Creates the config with the Hikari pool. */
    public DBConfig(DataSource dataSource) {
        this.dataSource = dataSource;
        try (Connection c = dataSource.getConnection()) {
            log.info("DB connectivity OK: {}", c.getMetaData().getDatabaseProductName());
        } catch (SQLException e) {
            log.warn("DB connectivity check failed at startup (app may still start): {}", e.getMessage());
        }
    }

    /** Returns the shared pool. */
    public DataSource getDataSource() {
        return dataSource;
    }
}
