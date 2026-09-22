package com.wallet;

import com.wallet.dao.LedgerDAO;
import com.wallet.dao.TransactionDAO;
import com.wallet.dao.WalletDAO;
import com.wallet.dto.TransferRequest;
import com.wallet.service.TransferService;
import com.wallet.util.TxManager;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Concurrency proof: 100 threads x Rs.10 A-&gt;B conserves money exactly.
 */
class ConcurrencyTest {

    /**
     * Fires 100 parallel Rs.10 transfers from a Rs.10,000 wallet.
     * Expects A=9,000, B=1,000 and total still 10,000.
     */
    @Test
    void hundredThreadsConserveMoney() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:conc_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1;MODE=MySQL;LOCK_TIMEOUT=60000");
        ds.setUser("sa");
        ds.setPassword("");
        TransferServiceTest.createSchema(ds);

        WalletDAO walletDAO = new WalletDAO();
        TransferService service = new TransferService(
                ds, new TxManager(ds), walletDAO, new TransactionDAO(), new LedgerDAO());

        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            s.execute("INSERT INTO users (name, email, password_hash) VALUES ('A','ca@t.com','x'),('B','cb@t.com','x')");
            s.execute("INSERT INTO wallets (user_id, balance) VALUES (1, 10000.00), (2, 0.00)");
        }

        int threads = 100;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                try {
                    start.await(30, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
                TransferRequest r = new TransferRequest();
                r.setFromWalletId(1L);
                r.setToWalletId(2L);
                r.setAmount(new BigDecimal("10.00"));
                r.setIdempotencyKey(UUID.randomUUID().toString());
                service.transfer(r);
                return null;
            }));
        }
        assertTrue(ready.await(30, TimeUnit.SECONDS), "threads did not get ready");
        start.countDown(); // release all 100 at once for maximum contention
        for (Future<?> f : futures) {
            f.get(120, TimeUnit.SECONDS); // rethrows any transfer failure
        }
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));

        BigDecimal a;
        BigDecimal b;
        try (Connection c = ds.getConnection()) {
            a = walletDAO.findById(c, 1).orElseThrow().getBalance();
            b = walletDAO.findById(c, 2).orElseThrow().getBalance();
        }
        assertEquals(0, new BigDecimal("9000.00").compareTo(a), "A should be 9000, was " + a);
        assertEquals(0, new BigDecimal("1000.00").compareTo(b), "B should be 1000, was " + b);
        assertEquals(0, new BigDecimal("10000.00").compareTo(a.add(b)), "money must be conserved");
    }

    // -----------------------------------------------------------------
    // VIVA DEMO — the WRONG version (DO NOT ENABLE):
    // If you remove "FOR UPDATE" from WalletDAO.findByIdForUpdate and
    // re-run this test, two threads can both read balance=10000,
    // both pass the >= check, and both subtract — the classic
    // lost-update. Final A then reads MORE than 9000 (e.g. 9010+)
    // because some debits were silently overwritten.
    //
    //   // WRONG: plain SELECT without a lock
    //   // SELECT ... FROM wallets WHERE wallet_id = ?
    //   // (no FOR UPDATE -> no row lock -> lost updates under contention)
    //
    // Keep it commented so `mvn test` stays green; uncomment live
    // in the viva to show the bug, then re-enable FOR UPDATE.
    // -----------------------------------------------------------------
    @SuppressWarnings("unused")
    private static DataSource unusedWrongVersionDemo(DataSource ds) {
        return ds;
    }
}
