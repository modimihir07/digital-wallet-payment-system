-- =====================================================================
-- 02_seed.sql : seed roles, demo users, wallets, merchants, bills
-- Run: mysql -u root -p wallet_db < sql/02_seed.sql
-- Password hashes below are BCrypt for 'password123' (demo only).
-- =====================================================================
USE wallet_db;

-- Roles
INSERT INTO roles (name) VALUES ('USER'), ('ADMIN')
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- Demo users (password = 'password123' for all three)
-- BCrypt hash (cost 10) of 'password123', generated with org.mindrot:jbcrypt.
INSERT INTO users (name, email, phone, password_hash, status) VALUES
('Aarav Sharma', 'aarav@example.com', '9000000001', '$2a$10$CyWQ7ofU9ESXobJ9CSgAHec4om1hGXLiY6yYyMHW14vDjcIUTFyf2', 'ACTIVE'),
('Diya Patel',   'diya@example.com',  '9000000002', '$2a$10$CyWQ7ofU9ESXobJ9CSgAHec4om1hGXLiY6yYyMHW14vDjcIUTFyf2', 'ACTIVE'),
('Admin User',   'admin@example.com', '9000000000', '$2a$10$CyWQ7ofU9ESXobJ9CSgAHec4om1hGXLiY6yYyMHW14vDjcIUTFyf2', 'ACTIVE')
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- Wallets (one per user, auto-created by app; seed mirrors that)
INSERT INTO wallets (user_id, balance, currency, status)
SELECT user_id, 5000.00, 'INR', 'ACTIVE' FROM users WHERE email = 'aarav@example.com'
ON DUPLICATE KEY UPDATE balance = VALUES(balance);

INSERT INTO wallets (user_id, balance, currency, status)
SELECT user_id, 3000.00, 'INR', 'ACTIVE' FROM users WHERE email = 'diya@example.com'
ON DUPLICATE KEY UPDATE balance = VALUES(balance);

INSERT INTO wallets (user_id, balance, currency, status)
SELECT user_id, 0.00, 'INR', 'ACTIVE' FROM users WHERE email = 'admin@example.com'
ON DUPLICATE KEY UPDATE balance = VALUES(balance);

-- Assign roles
INSERT IGNORE INTO user_roles (user_id, role_id)
SELECT u.user_id, r.role_id FROM users u JOIN roles r ON r.name = 'USER'
WHERE u.email IN ('aarav@example.com', 'diya@example.com');

INSERT IGNORE INTO user_roles (user_id, role_id)
SELECT u.user_id, r.role_id FROM users u JOIN roles r ON r.name = 'ADMIN'
WHERE u.email = 'admin@example.com';

-- Merchants (each gets no wallet by default; app top-up assigns on bill pay target)
INSERT INTO merchants (name, category) VALUES
('Torrent Power', 'Electricity'),
('Airtel Postpaid', 'Mobile'),
('Netflix India', 'Entertainment')
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- Sample bills for Aarav
INSERT INTO bills (user_id, merchant_id, amount, status, due_date)
SELECT u.user_id, m.merchant_id, 1250.00, 'UNPAID', CURDATE() + INTERVAL 7 DAY
FROM users u, merchants m
WHERE u.email = 'aarav@example.com' AND m.name = 'Torrent Power'
LIMIT 1;

INSERT INTO bills (user_id, merchant_id, amount, status, due_date)
SELECT u.user_id, m.merchant_id, 499.00, 'UNPAID', CURDATE() + INTERVAL 10 DAY
FROM users u, merchants m
WHERE u.email = 'diya@example.com' AND m.name = 'Airtel Postpaid'
LIMIT 1;
