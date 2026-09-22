-- =====================================================================
-- 01_schema.sql : Digital Wallet & Payment System - Full schema (MySQL 8)
-- Run: mysql -u root -p < sql/01_schema.sql
-- =====================================================================
CREATE DATABASE IF NOT EXISTS wallet_db
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE wallet_db;

SET TIME_ZONE = '+00:00';

-- ---------------- users ----------------
CREATE TABLE IF NOT EXISTS users (
    user_id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(100) NOT NULL,
    email         VARCHAR(150) NOT NULL UNIQUE,
    phone         VARCHAR(20) UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    status        ENUM('ACTIVE','BLOCKED') NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------- wallets ----------------
CREATE TABLE IF NOT EXISTS wallets (
    wallet_id  BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT NOT NULL UNIQUE,
    balance    DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    currency   CHAR(3) NOT NULL DEFAULT 'INR',
    status     ENUM('ACTIVE','FROZEN','CLOSED') NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_wallets_user FOREIGN KEY (user_id) REFERENCES users(user_id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT chk_balance CHECK (balance >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------- transactions ----------------
CREATE TABLE IF NOT EXISTS transactions (
    txn_id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    from_wallet     BIGINT NULL,
    to_wallet       BIGINT NULL,
    amount          DECIMAL(15,2) NOT NULL,
    type            ENUM('P2P','TOPUP','BILL','REFUND') NOT NULL,
    status          ENUM('PENDING','SUCCESS','FAILED','REVERSED') NOT NULL DEFAULT 'PENDING',
    idempotency_key VARCHAR(64) UNIQUE,
    remarks         VARCHAR(255),
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_txn_from FOREIGN KEY (from_wallet) REFERENCES wallets(wallet_id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_txn_to FOREIGN KEY (to_wallet) REFERENCES wallets(wallet_id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT chk_amount CHECK (amount > 0),
    INDEX idx_from_wallet (from_wallet),
    INDEX idx_to_wallet (to_wallet),
    INDEX idx_created_at (created_at),
    INDEX idx_status (status),
    INDEX idx_idem_key (idempotency_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------- ledger_entries (double-entry style, append-only) ----------------
CREATE TABLE IF NOT EXISTS ledger_entries (
    entry_id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    txn_id        BIGINT NOT NULL,
    wallet_id     BIGINT NOT NULL,
    entry_type    ENUM('DEBIT','CREDIT') NOT NULL,
    amount        DECIMAL(15,2) NOT NULL,
    balance_after DECIMAL(15,2) NOT NULL,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ledger_txn FOREIGN KEY (txn_id) REFERENCES transactions(txn_id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_ledger_wallet FOREIGN KEY (wallet_id) REFERENCES wallets(wallet_id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT chk_ledger_amount CHECK (amount > 0),
    INDEX idx_wallet_ledger (wallet_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------- merchants ----------------
CREATE TABLE IF NOT EXISTS merchants (
    merchant_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(150) NOT NULL,
    category    VARCHAR(50),
    wallet_id   BIGINT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_merchant_wallet FOREIGN KEY (wallet_id) REFERENCES wallets(wallet_id)
        ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------- bills ----------------
CREATE TABLE IF NOT EXISTS bills (
    bill_id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT NOT NULL,
    merchant_id  BIGINT NOT NULL,
    amount       DECIMAL(15,2) NOT NULL,
    status       ENUM('UNPAID','PAID','OVERDUE') NOT NULL DEFAULT 'UNPAID',
    due_date     DATE,
    paid_txn_id  BIGINT NULL,
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_bills_user FOREIGN KEY (user_id) REFERENCES users(user_id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_bills_merchant FOREIGN KEY (merchant_id) REFERENCES merchants(merchant_id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_bills_txn FOREIGN KEY (paid_txn_id) REFERENCES transactions(txn_id)
        ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT chk_bill_amount CHECK (amount > 0),
    INDEX idx_bills_user (user_id),
    INDEX idx_bills_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------- audit_log ----------------
CREATE TABLE IF NOT EXISTS audit_log (
    log_id     BIGINT AUTO_INCREMENT PRIMARY KEY,
    table_name VARCHAR(50) NOT NULL,
    action     VARCHAR(20) NOT NULL,
    record_id  BIGINT,
    old_value  JSON,
    new_value  JSON,
    changed_by BIGINT NULL,
    changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_audit_table (table_name),
    INDEX idx_audit_changed (changed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------- roles / user_roles (RBAC) ----------------
CREATE TABLE IF NOT EXISTS roles (
    role_id INT AUTO_INCREMENT PRIMARY KEY,
    name    VARCHAR(30) NOT NULL UNIQUE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS user_roles (
    user_id BIGINT NOT NULL,
    role_id INT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_ur_user FOREIGN KEY (user_id) REFERENCES users(user_id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_ur_role FOREIGN KEY (role_id) REFERENCES roles(role_id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
