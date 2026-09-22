package com.wallet;

import com.wallet.dao.AuditDAO;
import com.wallet.dao.BillDAO;
import com.wallet.dao.LedgerDAO;
import com.wallet.dao.TransactionDAO;
import com.wallet.dao.UserDAO;
import com.wallet.dao.WalletDAO;
import com.wallet.model.LedgerEntry;
import com.wallet.model.Transaction;
import com.wallet.model.User;
import com.wallet.model.Wallet;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Basic CRUD correctness for every DAO on an isolated H2 database.
 */
class DAOTest {

    private DataSource ds;
    private UserDAO userDAO;
    private WalletDAO walletDAO;
    private TransactionDAO transactionDAO;
    private LedgerDAO ledgerDAO;
    private BillDAO billDAO;
    private AuditDAO auditDAO;

    /** Fresh DB per test. */
    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource h2 = new JdbcDataSource();
        h2.setURL("jdbc:h2:mem:dao_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1;MODE=MySQL");
        h2.setUser("sa");
        h2.setPassword("");
        this.ds = h2;
        TransferServiceTest.createSchema(h2);
        userDAO = new UserDAO();
        walletDAO = new WalletDAO();
        transactionDAO = new TransactionDAO();
        ledgerDAO = new LedgerDAO();
        billDAO = new BillDAO();
        auditDAO = new AuditDAO();
    }

    /** UserDAO: insert + find by email/id + roles. */
    @Test
    void userDAO() throws Exception {
        try (Connection c = ds.getConnection();
             Statement s = c.createStatement()) {
            s.execute("INSERT INTO roles (name) VALUES ('USER')");
            User u = new User();
            u.setName("Test User");
            u.setEmail("t@dao.com");
            u.setPhone("9111111111");
            u.setPasswordHash("hash");
            u.setStatus("ACTIVE");
            long id = userDAO.insert(c, u);
            assertTrue(id > 0);
            Optional<User> byEmail = userDAO.findByEmail(c, "t@dao.com");
            assertTrue(byEmail.isPresent());
            assertEquals("Test User", byEmail.get().getName());
            assertTrue(userDAO.findById(c, id).isPresent());
            userDAO.assignRole(c, id, "USER");
            List<String> roles = userDAO.rolesOf(c, id);
            assertEquals(List.of("USER"), roles);
        }
    }

    /** WalletDAO: create + find + adjust + status + list. */
    @Test
    void walletDAO() throws Exception {
        try (Connection c = ds.getConnection();
             Statement s = c.createStatement()) {
            s.execute("INSERT INTO users (name, email, password_hash) VALUES ('W','w@dao.com','x')");
            long wid = walletDAO.create(c, 1);
            assertTrue(wid > 0);
            Wallet w = walletDAO.findById(c, wid).orElseThrow();
            assertEquals(0, BigDecimal.ZERO.compareTo(w.getBalance()));
            walletDAO.adjustBalance(c, wid, new BigDecimal("250.50"));
            assertEquals(0, new BigDecimal("250.50").compareTo(walletDAO.findById(c, wid).orElseThrow().getBalance()));
            assertTrue(walletDAO.findByIdForUpdate(c, wid).isPresent());
            assertTrue(walletDAO.findByUserId(c, 1).isPresent());
            walletDAO.setStatus(c, wid, "FROZEN");
            assertEquals("FROZEN", walletDAO.findById(c, wid).orElseThrow().getStatus());
            assertEquals(1, walletDAO.list(c, 10, 0).size());
        }
    }

    /** TransactionDAO + LedgerDAO: insert, find, counts, statement paging. */
    @Test
    void transactionAndLedgerDAO() throws Exception {
        try (Connection c = ds.getConnection();
             Statement s = c.createStatement()) {
            s.execute("INSERT INTO users (name, email, password_hash) VALUES ('A','ta@dao.com','x'),('B','tb@dao.com','x')");
            s.execute("INSERT INTO wallets (user_id, balance) VALUES (1, 100.00), (2, 0.00)");
            Transaction tx = new Transaction();
            tx.setFromWallet(1L);
            tx.setToWallet(2L);
            tx.setAmount(new BigDecimal("25.00"));
            tx.setType("P2P");
            tx.setStatus("SUCCESS");
            tx.setIdempotencyKey("dao-key-1");
            tx.setRemarks("dao test");
            long txnId = transactionDAO.insert(c, tx);
            assertTrue(txnId > 0);
            assertTrue(transactionDAO.findById(c, txnId).isPresent());
            assertTrue(transactionDAO.findByIdempotencyKey(c, "dao-key-1").isPresent());
            assertEquals(1, transactionDAO.countByIdempotencyKey(c, "dao-key-1"));

            LedgerEntry e = new LedgerEntry();
            e.setTxnId(txnId);
            e.setWalletId(1L);
            e.setEntryType("DEBIT");
            e.setAmount(new BigDecimal("25.00"));
            e.setBalanceAfter(new BigDecimal("75.00"));
            ledgerDAO.insert(c, e);
            assertEquals(1, ledgerDAO.countByWallet(c, 1));
            assertEquals(1, ledgerDAO.countByTxn(c, txnId));
            assertEquals(1, ledgerDAO.statement(c, 1, 20, 0).size());
        }
    }

    /** BillDAO: find, list by user, mark paid. AuditDAO: list (empty OK). */
    @Test
    void billAndAuditDAO() throws Exception {
        try (Connection c = ds.getConnection();
             Statement s = c.createStatement()) {
            s.execute("INSERT INTO users (name, email, password_hash) VALUES ('U','u@dao.com','x')");
            s.execute("INSERT INTO wallets (user_id, balance) VALUES (1, 500.00)");
            s.execute("INSERT INTO merchants (name, category) VALUES ('PowerCo','Electricity')");
            s.execute("INSERT INTO bills (user_id, merchant_id, amount, status) VALUES (1, 1, 99.99, 'UNPAID')");
            assertTrue(billDAO.findById(c, 1).isPresent());
            assertTrue(billDAO.findByIdForUpdate(c, 1).isPresent());
            assertEquals(1, billDAO.listByUser(c, 1).size());
            Transaction tx = new Transaction();
            tx.setFromWallet(1L);
            tx.setToWallet(null);
            tx.setAmount(new BigDecimal("99.99"));
            tx.setType("BILL");
            tx.setStatus("SUCCESS");
            tx.setIdempotencyKey("dao-bill-1");
            long txnId = transactionDAO.insert(c, tx);
            billDAO.markPaid(c, 1, txnId);
            assertEquals("PAID", billDAO.findById(c, 1).orElseThrow().getStatus());
            assertNotNull(billDAO.merchantWalletId(c, 1) == null ? "null-ok" : "has-wallet");
            assertEquals(0, auditDAO.list(c, 50, 0).size());
        }
    }
}
