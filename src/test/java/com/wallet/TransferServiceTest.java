package com.wallet;

import com.wallet.dao.LedgerDAO;
import com.wallet.dao.TransactionDAO;
import com.wallet.dao.WalletDAO;
import com.wallet.dto.TransferRequest;
import com.wallet.dto.TransferResponse;
import com.wallet.exception.InsufficientBalanceException;
import com.wallet.exception.WalletFrozenException;
import com.wallet.model.Wallet;
import com.wallet.service.TransferService;
import com.wallet.util.TxManager;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Functional tests for {@link TransferService} against in-memory H2
 * (same SQL semantics: FOR UPDATE, UNIQUE key, CHECK balance &gt;= 0).
 */
class TransferServiceTest {

    private DataSource dataSource;
    private TransferService transferService;
    private WalletDAO walletDAO;
    private LedgerDAO ledgerDAO;

    /** Minimal portable DDL mirroring sql/01_schema.sql (ENUMs as VARCHAR + CHECK). */
    static void createSchema(DataSource ds) throws SQLException {
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS users ("
                    + "user_id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(100),"
                    + " email VARCHAR(150) UNIQUE NOT NULL, phone VARCHAR(20) UNIQUE,"
                    + " password_hash VARCHAR(100), status VARCHAR(20) DEFAULT 'ACTIVE',"
                    + " created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
            s.execute("CREATE TABLE IF NOT EXISTS wallets ("
                    + "wallet_id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL UNIQUE,"
                    + " balance DECIMAL(15,2) DEFAULT 0.00, currency CHAR(3) DEFAULT 'INR',"
                    + " status VARCHAR(20) DEFAULT 'ACTIVE', created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,"
                    + " CONSTRAINT chk_balance CHECK (balance >= 0))");
            s.execute("CREATE TABLE IF NOT EXISTS transactions ("
                    + "txn_id BIGINT AUTO_INCREMENT PRIMARY KEY, from_wallet BIGINT NULL,"
                    + " to_wallet BIGINT NULL, amount DECIMAL(15,2) NOT NULL,"
                    + " type VARCHAR(20) NOT NULL, status VARCHAR(20) DEFAULT 'PENDING',"
                    + " idempotency_key VARCHAR(64) UNIQUE, remarks VARCHAR(255),"
                    + " created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,"
                    + " CONSTRAINT chk_amount CHECK (amount > 0))");
            s.execute("CREATE INDEX IF NOT EXISTS idx_from_wallet ON transactions(from_wallet)");
            s.execute("CREATE INDEX IF NOT EXISTS idx_to_wallet ON transactions(to_wallet)");
            s.execute("CREATE TABLE IF NOT EXISTS ledger_entries ("
                    + "entry_id BIGINT AUTO_INCREMENT PRIMARY KEY, txn_id BIGINT NOT NULL,"
                    + " wallet_id BIGINT NOT NULL, entry_type VARCHAR(10) NOT NULL,"
                    + " amount DECIMAL(15,2) NOT NULL, balance_after DECIMAL(15,2) NOT NULL,"
                    + " created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
            s.execute("CREATE TABLE IF NOT EXISTS merchants ("
                    + "merchant_id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(150) NOT NULL,"
                    + " category VARCHAR(50), wallet_id BIGINT NULL,"
                    + " created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
            s.execute("CREATE TABLE IF NOT EXISTS bills ("
                    + "bill_id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL,"
                    + " merchant_id BIGINT NOT NULL, amount DECIMAL(15,2) NOT NULL,"
                    + " status VARCHAR(20) DEFAULT 'UNPAID', due_date DATE, paid_txn_id BIGINT NULL,"
                    + " created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
            s.execute("CREATE TABLE IF NOT EXISTS audit_log ("
                    + "log_id BIGINT AUTO_INCREMENT PRIMARY KEY, table_name VARCHAR(50),"
                    + " action VARCHAR(20), record_id BIGINT, old_value VARCHAR(2000),"
                    + " new_value VARCHAR(2000), changed_by BIGINT,"
                    + " changed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
            s.execute("CREATE TABLE IF NOT EXISTS roles ("
                    + "role_id INT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(30) UNIQUE)");
            s.execute("CREATE TABLE IF NOT EXISTS user_roles ("
                    + "user_id BIGINT NOT NULL, role_id INT NOT NULL, PRIMARY KEY (user_id, role_id))");
        }
    }

    /** Fresh isolated DB + funded wallets A=1000, B=500 before each test. */
    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:transfer_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1;MODE=MySQL");
        ds.setUser("sa");
        ds.setPassword("");
        this.dataSource = ds;
        createSchema(ds);
        walletDAO = new WalletDAO();
        TransactionDAO transactionDAO = new TransactionDAO();
        ledgerDAO = new LedgerDAO();
        TxManager txManager = new TxManager(ds);
        transferService = new TransferService(ds, txManager, walletDAO, transactionDAO, ledgerDAO);
        try (Connection c = ds.getConnection();
             Statement s = c.createStatement()) {
            s.execute("INSERT INTO users (name, email, password_hash) VALUES ('A','a@t.com','x'),('B','b@t.com','x')");
            s.execute("INSERT INTO wallets (user_id, balance) VALUES (1, 1000.00), (2, 500.00)");
        }
    }

    private BigDecimal balance(long walletId) throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            Wallet w = walletDAO.findById(c, walletId).orElseThrow();
            return w.getBalance();
        }
    }

    private static TransferRequest req(long from, long to, String amount, String key) {
        TransferRequest r = new TransferRequest();
        r.setFromWalletId(from);
        r.setToWalletId(to);
        r.setAmount(new BigDecimal(amount));
        r.setIdempotencyKey(key);
        r.setRemarks("test");
        return r;
    }

    /** Happy path: A pays B 100; balances move and 2 ledger rows exist. */
    @Test
    void happyPath() throws Exception {
        TransferResponse resp = transferService.transfer(req(1, 2, "100.00", "key-happy-1"));
        assertEquals("SUCCESS", resp.getStatus());
        assertEquals(0, new BigDecimal("900.00").compareTo(balance(1)));
        assertEquals(0, new BigDecimal("600.00").compareTo(balance(2)));
        try (Connection c = dataSource.getConnection()) {
            assertEquals(2, ledgerDAO.countByTxn(c, resp.getTxnId()));
        }
    }

    /** Overdraft fails and writes no ledger rows. */
    @Test
    void insufficientBalance() throws Exception {
        assertThrows(InsufficientBalanceException.class,
                () -> transferService.transfer(req(1, 2, "99999.00", "key-poor-1")));
        assertEquals(0, new BigDecimal("1000.00").compareTo(balance(1)));
        try (Connection c = dataSource.getConnection()) {
            assertEquals(0, ledgerDAO.countByWallet(c, 1));
            assertEquals(0, ledgerDAO.countByWallet(c, 2));
        }
    }

    /** Frozen source wallet blocks the transfer. */
    @Test
    void frozenWallet() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            walletDAO.setStatus(c, 1, "FROZEN");
        }
        assertThrows(WalletFrozenException.class,
                () -> transferService.transfer(req(1, 2, "10.00", "key-frozen-1")));
    }

    /** Same idempotency key twice creates exactly one transaction. */
    @Test
    void idempotency() throws Exception {
        TransferResponse first = transferService.transfer(req(1, 2, "50.00", "key-idem-1"));
        TransferResponse second = transferService.transfer(req(1, 2, "50.00", "key-idem-1"));
        assertEquals(first.getTxnId(), second.getTxnId());
        assertTrue(second.isIdempotentReplay());
        try (Connection c = dataSource.getConnection();
             Statement s = c.createStatement();
             var rs = s.executeQuery("SELECT COUNT(*) FROM transactions WHERE idempotency_key='key-idem-1'")) {
            rs.next();
            assertEquals(1, rs.getInt(1));
        }
        // Balances moved only once.
        assertEquals(0, new BigDecimal("950.00").compareTo(balance(1)));
        assertEquals(0, new BigDecimal("550.00").compareTo(balance(2)));
    }
}
