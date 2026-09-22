-- =====================================================================
-- 05_views.sql : reporting views
-- Run: mysql -u root -p wallet_db < sql/05_views.sql
-- =====================================================================
USE wallet_db;

DROP VIEW IF EXISTS v_user_statement;
DROP VIEW IF EXISTS v_daily_summary;
DROP VIEW IF EXISTS v_top_users;
DROP VIEW IF EXISTS v_pending_bills;

-- Mini bank statement: every ledger entry with user/wallet/txn context
CREATE OR REPLACE VIEW v_user_statement AS
SELECT
    u.user_id,
    u.name AS user_name,
    u.email,
    w.wallet_id,
    w.balance AS current_balance,
    l.entry_id,
    l.txn_id,
    t.type AS txn_type,
    t.status AS txn_status,
    t.from_wallet,
    t.to_wallet,
    l.entry_type,
    l.amount,
    l.balance_after,
    t.remarks,
    l.created_at
FROM ledger_entries l
JOIN wallets w      ON w.wallet_id = l.wallet_id
JOIN users u        ON u.user_id = w.user_id
JOIN transactions t ON t.txn_id = l.txn_id;

-- Daily volume report
CREATE OR REPLACE VIEW v_daily_summary AS
SELECT
    DATE(created_at) AS txn_date,
    COUNT(*) AS total_txns,
    SUM(amount) AS total_volume,
    SUM(CASE WHEN status = 'SUCCESS' THEN 1 ELSE 0 END) AS success_count,
    SUM(CASE WHEN status <> 'SUCCESS' THEN 1 ELSE 0 END) AS fail_count
FROM transactions
GROUP BY DATE(created_at);

-- Users ranked by total outgoing (P2P + BILL) volume
CREATE OR REPLACE VIEW v_top_users AS
SELECT
    u.user_id,
    u.name AS user_name,
    u.email,
    COALESCE(SUM(t.amount), 0) AS total_outgoing,
    COUNT(t.txn_id) AS txn_count
FROM users u
LEFT JOIN wallets w ON w.user_id = u.user_id
LEFT JOIN transactions t ON t.from_wallet = w.wallet_id AND t.status = 'SUCCESS'
GROUP BY u.user_id, u.name, u.email
ORDER BY total_outgoing DESC;

-- Unpaid bills with user + merchant details
CREATE OR REPLACE VIEW v_pending_bills AS
SELECT
    b.bill_id,
    b.amount,
    b.status,
    b.due_date,
    b.created_at,
    u.user_id,
    u.name AS user_name,
    u.email,
    m.merchant_id,
    m.name AS merchant_name,
    m.category
FROM bills b
JOIN users u ON u.user_id = b.user_id
JOIN merchants m ON m.merchant_id = b.merchant_id
WHERE b.status IN ('UNPAID', 'OVERDUE');
