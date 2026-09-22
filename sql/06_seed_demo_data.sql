-- =====================================================================
-- 06_seed_demo_data.sql : Rich Demonstration Dataset for DBMS Capstone
-- Run: mysql -u root -p wallet_db < sql/06_seed_demo_data.sql
--
-- Contents:
--   - 15 Users with realistic Indian profiles (3 Admins, 12 Regular Users)
--   - Password for all demo accounts: Password@123
--   - 15 Wallets with varied starting balances
--   - 8 Major Merchants with dedicated merchant settlement wallets
--   - 40 Bills spread across users and merchants (mixed UNPAID & PAID)
--   - 100 Historical P2P transactions spread across the last 30 days
--   - 200 Double-entry ledger rows (DEBIT + CREDIT) with synchronized balance_after
-- =====================================================================

USE wallet_db;

DELIMITER $$

DROP PROCEDURE IF EXISTS sp_seed_demo_data$$

CREATE PROCEDURE sp_seed_demo_data()
proc: BEGIN
    DECLARE v_pw_hash VARCHAR(100);
    DECLARE v_user_role_id INT;
    DECLARE v_admin_role_id INT;
    DECLARE i INT DEFAULT 1;
    DECLARE v_from_user BIGINT;
    DECLARE v_to_user BIGINT;
    DECLARE v_from_wallet BIGINT;
    DECLARE v_to_wallet BIGINT;
    DECLARE v_amount DECIMAL(15,2);
    DECLARE v_from_bal DECIMAL(15,2);
    DECLARE v_to_bal DECIMAL(15,2);
    DECLARE v_new_from_bal DECIMAL(15,2);
    DECLARE v_new_to_bal DECIMAL(15,2);
    DECLARE v_txn_id BIGINT;
    DECLARE v_idem_key VARCHAR(64);
    DECLARE v_txn_time TIMESTAMP;
    DECLARE v_remark VARCHAR(255);

    -- Verified BCrypt hash (cost 10) for 'Password@123'
    SET v_pw_hash = '$2a$10$artn2JwockvJ7mfQD/tGJOvPMevlsQ/QsPaRRc1.WyVH8PKoCo7wy';

    -- 1. Ensure Roles Exist
    INSERT INTO roles (name) VALUES ('USER'), ('ADMIN')
    ON DUPLICATE KEY UPDATE name = VALUES(name);

    SELECT role_id INTO v_user_role_id FROM roles WHERE name = 'USER' LIMIT 1;
    SELECT role_id INTO v_admin_role_id FROM roles WHERE name = 'ADMIN' LIMIT 1;

    -- 2. Insert 15 Demo Users (3 Admins + 12 Users)
    INSERT INTO users (name, email, phone, password_hash, status) VALUES
    ('Rohan Sharma',      'rohan.sharma@example.com',     '9820011001', v_pw_hash, 'ACTIVE'),
    ('Priya Verma',       'priya.verma@example.com',      '9820011002', v_pw_hash, 'ACTIVE'),
    ('Amit Patel',        'amit.patel@example.com',       '9820011003', v_pw_hash, 'ACTIVE'),
    ('Sneha Reddy',       'sneha.reddy@example.com',      '9820011004', v_pw_hash, 'ACTIVE'),
    ('Ananya Iyer',       'ananya.iyer@example.com',      '9820011005', v_pw_hash, 'ACTIVE'),
    ('Rajesh Nair',       'rajesh.nair@example.com',      '9820011006', v_pw_hash, 'ACTIVE'),
    ('Pooja Gupta',       'pooja.gupta@example.com',      '9820011007', v_pw_hash, 'ACTIVE'),
    ('Vikram Singh',      'vikram.singh@example.com',     '9820011008', v_pw_hash, 'ACTIVE'),
    ('Neha Joshi',        'neha.joshi@example.com',       '9820011009', v_pw_hash, 'ACTIVE'),
    ('Aditya Kulkarni',   'aditya.kulkarni@example.com',  '9820011010', v_pw_hash, 'ACTIVE'),
    ('Kavita Rao',        'kavita.rao@example.com',       '9820011011', v_pw_hash, 'ACTIVE'),
    ('Manish Mehta',      'manish.mehta@example.com',     '9820011012', v_pw_hash, 'ACTIVE'),
    ('Divya Choudhury',   'divya.choudhury@example.com',  '9820011013', v_pw_hash, 'ACTIVE'),
    ('Suresh Kumar',      'suresh.kumar@example.com',     '9820011014', v_pw_hash, 'ACTIVE'),
    ('Meera Nambiar',     'meera.nambiar@example.com',    '9820011015', v_pw_hash, 'ACTIVE')
    ON DUPLICATE KEY UPDATE password_hash = VALUES(password_hash), name = VALUES(name);

    -- Also insert 8 Merchant Corporate accounts to back merchant settlement wallets
    INSERT INTO users (name, email, phone, password_hash, status) VALUES
    ('Amazon India Corp', 'amazon.corp@example.com',    '9800010001', v_pw_hash, 'ACTIVE'),
    ('Zomato Corp',       'zomato.corp@example.com',    '9800010002', v_pw_hash, 'ACTIVE'),
    ('Swiggy Corp',       'swiggy.corp@example.com',    '9800010003', v_pw_hash, 'ACTIVE'),
    ('Flipkart Corp',     'flipkart.corp@example.com',  '9800010004', v_pw_hash, 'ACTIVE'),
    ('Uber Corp',         'uber.corp@example.com',      '9800010005', v_pw_hash, 'ACTIVE'),
    ('Netflix Corp',      'netflix.corp@example.com',   '9800010006', v_pw_hash, 'ACTIVE'),
    ('Airtel Corp',       'airtel.corp@example.com',    '9800010007', v_pw_hash, 'ACTIVE'),
    ('IRCTC Corp',        'irctc.corp@example.com',     '9800010008', v_pw_hash, 'ACTIVE')
    ON DUPLICATE KEY UPDATE password_hash = VALUES(password_hash);

    -- 3. Assign Roles
    -- First 3 users are ADMIN + USER
    INSERT IGNORE INTO user_roles (user_id, role_id)
    SELECT user_id, v_admin_role_id FROM users
    WHERE email IN ('rohan.sharma@example.com', 'priya.verma@example.com', 'amit.patel@example.com');

    -- All users get USER role
    INSERT IGNORE INTO user_roles (user_id, role_id)
    SELECT user_id, v_user_role_id FROM users;

    -- 4. Create Wallets with Varied Base Balances
    INSERT INTO wallets (user_id, balance, currency, status)
    SELECT u.user_id, 25000.00, 'INR', 'ACTIVE' FROM users u WHERE u.email = 'rohan.sharma@example.com'
    ON DUPLICATE KEY UPDATE status = 'ACTIVE';

    INSERT INTO wallets (user_id, balance, currency, status)
    SELECT u.user_id, 18500.00, 'INR', 'ACTIVE' FROM users u WHERE u.email = 'priya.verma@example.com'
    ON DUPLICATE KEY UPDATE status = 'ACTIVE';

    INSERT INTO wallets (user_id, balance, currency, status)
    SELECT u.user_id, 32000.00, 'INR', 'ACTIVE' FROM users u WHERE u.email = 'amit.patel@example.com'
    ON DUPLICATE KEY UPDATE status = 'ACTIVE';

    INSERT INTO wallets (user_id, balance, currency, status)
    SELECT u.user_id, 12000.00, 'INR', 'ACTIVE' FROM users u WHERE u.email = 'sneha.reddy@example.com'
    ON DUPLICATE KEY UPDATE status = 'ACTIVE';

    INSERT INTO wallets (user_id, balance, currency, status)
    SELECT u.user_id, 15500.00, 'INR', 'ACTIVE' FROM users u WHERE u.email = 'ananya.iyer@example.com'
    ON DUPLICATE KEY UPDATE status = 'ACTIVE';

    INSERT INTO wallets (user_id, balance, currency, status)
    SELECT u.user_id, 8500.00, 'INR', 'ACTIVE' FROM users u WHERE u.email = 'rajesh.nair@example.com'
    ON DUPLICATE KEY UPDATE status = 'ACTIVE';

    INSERT INTO wallets (user_id, balance, currency, status)
    SELECT u.user_id, 22000.00, 'INR', 'ACTIVE' FROM users u WHERE u.email = 'pooja.gupta@example.com'
    ON DUPLICATE KEY UPDATE status = 'ACTIVE';

    INSERT INTO wallets (user_id, balance, currency, status)
    SELECT u.user_id, 14200.00, 'INR', 'ACTIVE' FROM users u WHERE u.email = 'vikram.singh@example.com'
    ON DUPLICATE KEY UPDATE status = 'ACTIVE';

    INSERT INTO wallets (user_id, balance, currency, status)
    SELECT u.user_id, 9800.00, 'INR', 'ACTIVE' FROM users u WHERE u.email = 'neha.joshi@example.com'
    ON DUPLICATE KEY UPDATE status = 'ACTIVE';

    INSERT INTO wallets (user_id, balance, currency, status)
    SELECT u.user_id, 29000.00, 'INR', 'ACTIVE' FROM users u WHERE u.email = 'aditya.kulkarni@example.com'
    ON DUPLICATE KEY UPDATE status = 'ACTIVE';

    INSERT INTO wallets (user_id, balance, currency, status)
    SELECT u.user_id, 7500.00, 'INR', 'ACTIVE' FROM users u WHERE u.email = 'kavita.rao@example.com'
    ON DUPLICATE KEY UPDATE status = 'ACTIVE';

    INSERT INTO wallets (user_id, balance, currency, status)
    SELECT u.user_id, 16000.00, 'INR', 'ACTIVE' FROM users u WHERE u.email = 'manish.mehta@example.com'
    ON DUPLICATE KEY UPDATE status = 'ACTIVE';

    INSERT INTO wallets (user_id, balance, currency, status)
    SELECT u.user_id, 11500.00, 'INR', 'ACTIVE' FROM users u WHERE u.email = 'divya.choudhury@example.com'
    ON DUPLICATE KEY UPDATE status = 'ACTIVE';

    INSERT INTO wallets (user_id, balance, currency, status)
    SELECT u.user_id, 6200.00, 'INR', 'ACTIVE' FROM users u WHERE u.email = 'suresh.kumar@example.com'
    ON DUPLICATE KEY UPDATE status = 'ACTIVE';

    INSERT INTO wallets (user_id, balance, currency, status)
    SELECT u.user_id, 19500.00, 'INR', 'ACTIVE' FROM users u WHERE u.email = 'meera.nambiar@example.com'
    ON DUPLICATE KEY UPDATE status = 'ACTIVE';

    -- Merchant Wallets (starting with baseline business balance)
    INSERT INTO wallets (user_id, balance, currency, status)
    SELECT u.user_id, 50000.00, 'INR', 'ACTIVE' FROM users u
    WHERE u.email IN (
        'amazon.corp@example.com', 'zomato.corp@example.com', 'swiggy.corp@example.com',
        'flipkart.corp@example.com', 'uber.corp@example.com', 'netflix.corp@example.com',
        'airtel.corp@example.com', 'irctc.corp@example.com'
    )
    ON DUPLICATE KEY UPDATE status = 'ACTIVE';

    -- 5. Insert 8 Merchants with Linked Wallets
    INSERT INTO merchants (name, category, wallet_id)
    SELECT 'Amazon India', 'Shopping', w.wallet_id FROM users u JOIN wallets w ON w.user_id = u.user_id WHERE u.email = 'amazon.corp@example.com'
    ON DUPLICATE KEY UPDATE category = VALUES(category);

    INSERT INTO merchants (name, category, wallet_id)
    SELECT 'Zomato', 'Food', w.wallet_id FROM users u JOIN wallets w ON w.user_id = u.user_id WHERE u.email = 'zomato.corp@example.com'
    ON DUPLICATE KEY UPDATE category = VALUES(category);

    INSERT INTO merchants (name, category, wallet_id)
    SELECT 'Swiggy', 'Food', w.wallet_id FROM users u JOIN wallets w ON w.user_id = u.user_id WHERE u.email = 'swiggy.corp@example.com'
    ON DUPLICATE KEY UPDATE category = VALUES(category);

    INSERT INTO merchants (name, category, wallet_id)
    SELECT 'Flipkart', 'Shopping', w.wallet_id FROM users u JOIN wallets w ON w.user_id = u.user_id WHERE u.email = 'flipkart.corp@example.com'
    ON DUPLICATE KEY UPDATE category = VALUES(category);

    INSERT INTO merchants (name, category, wallet_id)
    SELECT 'Uber', 'Transport', w.wallet_id FROM users u JOIN wallets w ON w.user_id = u.user_id WHERE u.email = 'uber.corp@example.com'
    ON DUPLICATE KEY UPDATE category = VALUES(category);

    INSERT INTO merchants (name, category, wallet_id)
    SELECT 'Netflix India', 'Entertainment', w.wallet_id FROM users u JOIN wallets w ON w.user_id = u.user_id WHERE u.email = 'netflix.corp@example.com'
    ON DUPLICATE KEY UPDATE category = VALUES(category);

    INSERT INTO merchants (name, category, wallet_id)
    SELECT 'Airtel Postpaid', 'Recharge', w.wallet_id FROM users u JOIN wallets w ON w.user_id = u.user_id WHERE u.email = 'airtel.corp@example.com'
    ON DUPLICATE KEY UPDATE category = VALUES(category);

    INSERT INTO merchants (name, category, wallet_id)
    SELECT 'IRCTC', 'Travel', w.wallet_id FROM users u JOIN wallets w ON w.user_id = u.user_id WHERE u.email = 'irctc.corp@example.com'
    ON DUPLICATE KEY UPDATE category = VALUES(category);

    -- 6. Insert 40 Realistic Bills across Users and Merchants
    -- Delete previous demo bills to ensure fresh clean state
    DELETE FROM bills WHERE user_id IN (
        SELECT user_id FROM users WHERE email LIKE '%@example.com' AND email NOT LIKE '%.corp@example.com'
    );

    INSERT INTO bills (user_id, merchant_id, amount, status, due_date)
    SELECT u.user_id, m.merchant_id, 649.00, 'UNPAID', CURDATE() + INTERVAL 5 DAY
    FROM users u, merchants m WHERE u.email = 'rohan.sharma@example.com' AND m.name = 'Netflix India' LIMIT 1;

    INSERT INTO bills (user_id, merchant_id, amount, status, due_date)
    SELECT u.user_id, m.merchant_id, 499.00, 'UNPAID', CURDATE() + INTERVAL 3 DAY
    FROM users u, merchants m WHERE u.email = 'rohan.sharma@example.com' AND m.name = 'Airtel Postpaid' LIMIT 1;

    INSERT INTO bills (user_id, merchant_id, amount, status, due_date)
    SELECT u.user_id, m.merchant_id, 1420.00, 'PAID', CURDATE() - INTERVAL 12 DAY
    FROM users u, merchants m WHERE u.email = 'rohan.sharma@example.com' AND m.name = 'Amazon India' LIMIT 1;

    INSERT INTO bills (user_id, merchant_id, amount, status, due_date)
    SELECT u.user_id, m.merchant_id, 350.00, 'PAID', CURDATE() - INTERVAL 4 DAY
    FROM users u, merchants m WHERE u.email = 'rohan.sharma@example.com' AND m.name = 'Zomato' LIMIT 1;

    INSERT INTO bills (user_id, merchant_id, amount, status, due_date)
    SELECT u.user_id, m.merchant_id, 1299.00, 'UNPAID', CURDATE() + INTERVAL 8 DAY
    FROM users u, merchants m WHERE u.email = 'priya.verma@example.com' AND m.name = 'Flipkart' LIMIT 1;

    INSERT INTO bills (user_id, merchant_id, amount, status, due_date)
    SELECT u.user_id, m.merchant_id, 450.00, 'PAID', CURDATE() - INTERVAL 2 DAY
    FROM users u, merchants m WHERE u.email = 'priya.verma@example.com' AND m.name = 'Swiggy' LIMIT 1;

    INSERT INTO bills (user_id, merchant_id, amount, status, due_date)
    SELECT u.user_id, m.merchant_id, 799.00, 'UNPAID', CURDATE() + INTERVAL 10 DAY
    FROM users u, merchants m WHERE u.email = 'priya.verma@example.com' AND m.name = 'Airtel Postpaid' LIMIT 1;

    INSERT INTO bills (user_id, merchant_id, amount, status, due_date)
    SELECT u.user_id, m.merchant_id, 649.00, 'PAID', CURDATE() - INTERVAL 15 DAY
    FROM users u, merchants m WHERE u.email = 'priya.verma@example.com' AND m.name = 'Netflix India' LIMIT 1;

    INSERT INTO bills (user_id, merchant_id, amount, status, due_date)
    SELECT u.user_id, m.merchant_id, 1850.00, 'UNPAID', CURDATE() + INTERVAL 2 DAY
    FROM users u, merchants m WHERE u.email = 'amit.patel@example.com' AND m.name = 'IRCTC' LIMIT 1;

    INSERT INTO bills (user_id, merchant_id, amount, status, due_date)
    SELECT u.user_id, m.merchant_id, 520.00, 'PAID', CURDATE() - INTERVAL 6 DAY
    FROM users u, merchants m WHERE u.email = 'amit.patel@example.com' AND m.name = 'Uber' LIMIT 1;

    INSERT INTO bills (user_id, merchant_id, amount, status, due_date)
    SELECT u.user_id, m.merchant_id, 2499.00, 'UNPAID', CURDATE() + INTERVAL 12 DAY
    FROM users u, merchants m WHERE u.email = 'amit.patel@example.com' AND m.name = 'Amazon India' LIMIT 1;

    INSERT INTO bills (user_id, merchant_id, amount, status, due_date)
    SELECT u.user_id, m.merchant_id, 380.00, 'PAID', CURDATE() - INTERVAL 8 DAY
    FROM users u, merchants m WHERE u.email = 'sneha.reddy@example.com' AND m.name = 'Zomato' LIMIT 1;

    INSERT INTO bills (user_id, merchant_id, amount, status, due_date)
    SELECT u.user_id, m.merchant_id, 649.00, 'UNPAID', CURDATE() + INTERVAL 4 DAY
    FROM users u, merchants m WHERE u.email = 'sneha.reddy@example.com' AND m.name = 'Netflix India' LIMIT 1;

    INSERT INTO bills (user_id, merchant_id, amount, status, due_date)
    SELECT u.user_id, m.merchant_id, 750.00, 'PAID', CURDATE() - INTERVAL 14 DAY
    FROM users u, merchants m WHERE u.email = 'ananya.iyer@example.com' AND m.name = 'Uber' LIMIT 1;

    INSERT INTO bills (user_id, merchant_id, amount, status, due_date)
    SELECT u.user_id, m.merchant_id, 499.00, 'UNPAID', CURDATE() + INTERVAL 7 DAY
    FROM users u, merchants m WHERE u.email = 'ananya.iyer@example.com' AND m.name = 'Airtel Postpaid' LIMIT 1;

    INSERT INTO bills (user_id, merchant_id, amount, status, due_date)
    SELECT u.user_id, m.merchant_id, 1690.00, 'PAID', CURDATE() - INTERVAL 9 DAY
    FROM users u, merchants m WHERE u.email = 'ananya.iyer@example.com' AND m.name = 'Flipkart' LIMIT 1;

    INSERT INTO bills (user_id, merchant_id, amount, status, due_date)
    SELECT u.user_id, m.merchant_id, 420.00, 'UNPAID', CURDATE() + INTERVAL 1 DAY
    FROM users u, merchants m WHERE u.email = 'rajesh.nair@example.com' AND m.name = 'Swiggy' LIMIT 1;

    INSERT INTO bills (user_id, merchant_id, amount, status, due_date)
    SELECT u.user_id, m.merchant_id, 1250.00, 'PAID', CURDATE() - INTERVAL 18 DAY
    FROM users u, merchants m WHERE u.email = 'rajesh.nair@example.com' AND m.name = 'IRCTC' LIMIT 1;

    INSERT INTO bills (user_id, merchant_id, amount, status, due_date)
    SELECT u.user_id, m.merchant_id, 649.00, 'UNPAID', CURDATE() + INTERVAL 11 DAY
    FROM users u, merchants m WHERE u.email = 'pooja.gupta@example.com' AND m.name = 'Netflix India' LIMIT 1;

    INSERT INTO bills (user_id, merchant_id, amount, status, due_date)
    SELECT u.user_id, m.merchant_id, 2890.00, 'PAID', CURDATE() - INTERVAL 5 DAY
    FROM users u, merchants m WHERE u.email = 'pooja.gupta@example.com' AND m.name = 'Amazon India' LIMIT 1;

    INSERT INTO bills (user_id, merchant_id, amount, status, due_date)
    SELECT u.user_id, m.merchant_id, 340.00, 'UNPAID', CURDATE() + INTERVAL 6 DAY
    FROM users u, merchants m WHERE u.email = 'pooja.gupta@example.com' AND m.name = 'Uber' LIMIT 1;

    INSERT INTO bills (user_id, merchant_id, amount, status, due_date)
    SELECT u.user_id, m.merchant_id, 999.00, 'PAID', CURDATE() - INTERVAL 20 DAY
    FROM users u, merchants m WHERE u.email = 'vikram.singh@example.com' AND m.name = 'Airtel Postpaid' LIMIT 1;

    INSERT INTO bills (user_id, merchant_id, amount, status, due_date)
    SELECT u.user_id, m.merchant_id, 480.00, 'UNPAID', CURDATE() + INTERVAL 3 DAY
    FROM users u, merchants m WHERE u.email = 'vikram.singh@example.com' AND m.name = 'Zomato' LIMIT 1;

    INSERT INTO bills (user_id, merchant_id, amount, status, due_date)
    SELECT u.user_id, m.merchant_id, 1550.00, 'PAID', CURDATE() - INTERVAL 11 DAY
    FROM users u, merchants m WHERE u.email = 'neha.joshi@example.com' AND m.name = 'Flipkart' LIMIT 1;

    INSERT INTO bills (user_id, merchant_id, amount, status, due_date)
    SELECT u.user_id, m.merchant_id, 649.00, 'UNPAID', CURDATE() + INTERVAL 9 DAY
    FROM users u, merchants m WHERE u.email = 'neha.joshi@example.com' AND m.name = 'Netflix India' LIMIT 1;

    INSERT INTO bills (user_id, merchant_id, amount, status, due_date)
    SELECT u.user_id, m.merchant_id, 2100.00, 'PAID', CURDATE() - INTERVAL 7 DAY
    FROM users u, merchants m WHERE u.email = 'aditya.kulkarni@example.com' AND m.name = 'IRCTC' LIMIT 1;

    INSERT INTO bills (user_id, merchant_id, amount, status, due_date)
    SELECT u.user_id, m.merchant_id, 310.00, 'UNPAID', CURDATE() + INTERVAL 4 DAY
    FROM users u, merchants m WHERE u.email = 'aditya.kulkarni@example.com' AND m.name = 'Swiggy' LIMIT 1;

    INSERT INTO bills (user_id, merchant_id, amount, status, due_date)
    SELECT u.user_id, m.merchant_id, 499.00, 'PAID', CURDATE() - INTERVAL 16 DAY
    FROM users u, merchants m WHERE u.email = 'kavita.rao@example.com' AND m.name = 'Airtel Postpaid' LIMIT 1;

    INSERT INTO bills (user_id, merchant_id, amount, status, due_date)
    SELECT u.user_id, m.merchant_id, 890.00, 'UNPAID', CURDATE() + INTERVAL 8 DAY
    FROM users u, merchants m WHERE u.email = 'kavita.rao@example.com' AND m.name = 'Amazon India' LIMIT 1;

    INSERT INTO bills (user_id, merchant_id, amount, status, due_date)
    SELECT u.user_id, m.merchant_id, 640.00, 'PAID', CURDATE() - INTERVAL 13 DAY
    FROM users u, merchants m WHERE u.email = 'manish.mehta@example.com' AND m.name = 'Uber' LIMIT 1;

    INSERT INTO bills (user_id, merchant_id, amount, status, due_date)
    SELECT u.user_id, m.merchant_id, 649.00, 'UNPAID', CURDATE() + INTERVAL 5 DAY
    FROM users u, merchants m WHERE u.email = 'manish.mehta@example.com' AND m.name = 'Netflix India' LIMIT 1;

    INSERT INTO bills (user_id, merchant_id, amount, status, due_date)
    SELECT u.user_id, m.merchant_id, 450.00, 'PAID', CURDATE() - INTERVAL 3 DAY
    FROM users u, merchants m WHERE u.email = 'divya.choudhury@example.com' AND m.name = 'Zomato' LIMIT 1;

    INSERT INTO bills (user_id, merchant_id, amount, status, due_date)
    SELECT u.user_id, m.merchant_id, 1200.00, 'UNPAID', CURDATE() + INTERVAL 14 DAY
    FROM users u, merchants m WHERE u.email = 'divya.choudhury@example.com' AND m.name = 'Flipkart' LIMIT 1;

    INSERT INTO bills (user_id, merchant_id, amount, status, due_date)
    SELECT u.user_id, m.merchant_id, 320.00, 'PAID', CURDATE() - INTERVAL 10 DAY
    FROM users u, merchants m WHERE u.email = 'suresh.kumar@example.com' AND m.name = 'Swiggy' LIMIT 1;

    INSERT INTO bills (user_id, merchant_id, amount, status, due_date)
    SELECT u.user_id, m.merchant_id, 499.00, 'UNPAID', CURDATE() + INTERVAL 2 DAY
    FROM users u, merchants m WHERE u.email = 'suresh.kumar@example.com' AND m.name = 'Airtel Postpaid' LIMIT 1;

    INSERT INTO bills (user_id, merchant_id, amount, status, due_date)
    SELECT u.user_id, m.merchant_id, 1750.00, 'PAID', CURDATE() - INTERVAL 4 DAY
    FROM users u, merchants m WHERE u.email = 'meera.nambiar@example.com' AND m.name = 'Amazon India' LIMIT 1;

    INSERT INTO bills (user_id, merchant_id, amount, status, due_date)
    SELECT u.user_id, m.merchant_id, 649.00, 'UNPAID', CURDATE() + INTERVAL 12 DAY
    FROM users u, merchants m WHERE u.email = 'meera.nambiar@example.com' AND m.name = 'Netflix India' LIMIT 1;

    INSERT INTO bills (user_id, merchant_id, amount, status, due_date)
    SELECT u.user_id, m.merchant_id, 510.00, 'PAID', CURDATE() - INTERVAL 1 DAY
    FROM users u, merchants m WHERE u.email = 'meera.nambiar@example.com' AND m.name = 'Uber' LIMIT 1;

    INSERT INTO bills (user_id, merchant_id, amount, status, due_date)
    SELECT u.user_id, m.merchant_id, 850.00, 'UNPAID', CURDATE() + INTERVAL 6 DAY
    FROM users u, merchants m WHERE u.email = 'meera.nambiar@example.com' AND m.name = 'IRCTC' LIMIT 1;

    INSERT INTO bills (user_id, merchant_id, amount, status, due_date)
    SELECT u.user_id, m.merchant_id, 430.00, 'PAID', CURDATE() - INTERVAL 8 DAY
    FROM users u, merchants m WHERE u.email = 'suresh.kumar@example.com' AND m.name = 'Zomato' LIMIT 1;

    -- 7. 100 Historical Transactions & Balanced Double-Entry Ledger
    -- Create temporary table to dynamically calculate running balance per wallet
    DROP TEMPORARY TABLE IF EXISTS tmp_running_wallets;
    CREATE TEMPORARY TABLE tmp_running_wallets (
        user_num INT PRIMARY KEY,
        wallet_id BIGINT,
        current_balance DECIMAL(15,2)
    );

    -- Load the 15 user wallets into temporary table with their baseline balances
    INSERT INTO tmp_running_wallets (user_num, wallet_id, current_balance)
    SELECT 1, w.wallet_id, 25000.00 FROM users u JOIN wallets w ON w.user_id = u.user_id WHERE u.email = 'rohan.sharma@example.com'
    UNION ALL
    SELECT 2, w.wallet_id, 18500.00 FROM users u JOIN wallets w ON w.user_id = u.user_id WHERE u.email = 'priya.verma@example.com'
    UNION ALL
    SELECT 3, w.wallet_id, 32000.00 FROM users u JOIN wallets w ON w.user_id = u.user_id WHERE u.email = 'amit.patel@example.com'
    UNION ALL
    SELECT 4, w.wallet_id, 12000.00 FROM users u JOIN wallets w ON w.user_id = u.user_id WHERE u.email = 'sneha.reddy@example.com'
    UNION ALL
    SELECT 5, w.wallet_id, 15500.00 FROM users u JOIN wallets w ON w.user_id = u.user_id WHERE u.email = 'ananya.iyer@example.com'
    UNION ALL
    SELECT 6, w.wallet_id, 8500.00 FROM users u JOIN wallets w ON w.user_id = u.user_id WHERE u.email = 'rajesh.nair@example.com'
    UNION ALL
    SELECT 7, w.wallet_id, 22000.00 FROM users u JOIN wallets w ON w.user_id = u.user_id WHERE u.email = 'pooja.gupta@example.com'
    UNION ALL
    SELECT 8, w.wallet_id, 14200.00 FROM users u JOIN wallets w ON w.user_id = u.user_id WHERE u.email = 'vikram.singh@example.com'
    UNION ALL
    SELECT 9, w.wallet_id, 9800.00 FROM users u JOIN wallets w ON w.user_id = u.user_id WHERE u.email = 'neha.joshi@example.com'
    UNION ALL
    SELECT 10, w.wallet_id, 29000.00 FROM users u JOIN wallets w ON w.user_id = u.user_id WHERE u.email = 'aditya.kulkarni@example.com'
    UNION ALL
    SELECT 11, w.wallet_id, 7500.00 FROM users u JOIN wallets w ON w.user_id = u.user_id WHERE u.email = 'kavita.rao@example.com'
    UNION ALL
    SELECT 12, w.wallet_id, 16000.00 FROM users u JOIN wallets w ON w.user_id = u.user_id WHERE u.email = 'manish.mehta@example.com'
    UNION ALL
    SELECT 13, w.wallet_id, 11500.00 FROM users u JOIN wallets w ON w.user_id = u.user_id WHERE u.email = 'divya.choudhury@example.com'
    UNION ALL
    SELECT 14, w.wallet_id, 6200.00 FROM users u JOIN wallets w ON w.user_id = u.user_id WHERE u.email = 'suresh.kumar@example.com'
    UNION ALL
    SELECT 15, w.wallet_id, 19500.00 FROM users u JOIN wallets w ON w.user_id = u.user_id WHERE u.email = 'meera.nambiar@example.com';

    -- Clean any previous seed transactions to preserve clean state
    DELETE FROM ledger_entries WHERE txn_id IN (
        SELECT txn_id FROM transactions WHERE idempotency_key LIKE 'demo_p2p_seed_%'
    );
    DELETE FROM transactions WHERE idempotency_key LIKE 'demo_p2p_seed_%';

    -- Generate exactly 100 historical P2P transactions with ledger entries
    SET i = 1;
    WHILE i <= 100 DO
        -- Deterministic rotation among the 15 users
        SET v_from_user = ((i - 1) % 15) + 1;
        SET v_to_user = (((i - 1) + 4) % 15) + 1;
        IF v_from_user = v_to_user THEN
            SET v_to_user = (((v_from_user) % 15) + 1);
        END IF;

        -- Varied transaction amount between ₹25 and ₹450
        SET v_amount = ROUND(((i * 23) % 400) + 25.00, 2);

        -- Lookup wallets and current running balances
        SELECT wallet_id, current_balance INTO v_from_wallet, v_from_bal
        FROM tmp_running_wallets WHERE user_num = v_from_user;

        SELECT wallet_id, current_balance INTO v_to_wallet, v_to_bal
        FROM tmp_running_wallets WHERE user_num = v_to_user;

        -- Only execute if sender has sufficient balance
        IF v_from_bal >= v_amount THEN
            SET v_new_from_bal = v_from_bal - v_amount;
            SET v_new_to_bal = v_to_bal + v_amount;

            -- Update temporary tracking balances
            UPDATE tmp_running_wallets SET current_balance = v_new_from_bal WHERE user_num = v_from_user;
            UPDATE tmp_running_wallets SET current_balance = v_new_to_bal WHERE user_num = v_to_user;

            -- Timestamp spread over last 30 days
            SET v_txn_time = NOW() - INTERVAL FLOOR(30 - (i * 0.28)) DAY + INTERVAL (i * 11) MINUTE;
            SET v_idem_key = CONCAT('demo_p2p_seed_', LPAD(i, 4, '0'));
            SET v_remark = CONCAT('Settlement #', i, ' for services/groceries');

            -- Insert Transaction
            INSERT INTO transactions (from_wallet, to_wallet, amount, type, status, idempotency_key, remarks, created_at)
            VALUES (v_from_wallet, v_to_wallet, v_amount, 'P2P', 'SUCCESS', v_idem_key, v_remark, v_txn_time);
            SET v_txn_id = LAST_INSERT_ID();

            -- Insert Paired Double-Entry Ledger Rows
            INSERT INTO ledger_entries (txn_id, wallet_id, entry_type, amount, balance_after, created_at)
            VALUES (v_txn_id, v_from_wallet, 'DEBIT', v_amount, v_new_from_bal, v_txn_time);

            INSERT INTO ledger_entries (txn_id, wallet_id, entry_type, amount, balance_after, created_at)
            VALUES (v_txn_id, v_to_wallet, 'CREDIT', v_amount, v_new_to_bal, v_txn_time);
        END IF;

        SET i = i + 1;
    END WHILE;

    -- Synchronize final calculated running balances into the real wallets table
    UPDATE wallets w
    JOIN tmp_running_wallets tmp ON tmp.wallet_id = w.wallet_id
    SET w.balance = tmp.current_balance;

    DROP TEMPORARY TABLE IF EXISTS tmp_running_wallets;

END$$

DELIMITER ;

-- Execute seed procedure
CALL sp_seed_demo_data();

-- Drop temporary setup procedure
DROP PROCEDURE IF EXISTS sp_seed_demo_data;
