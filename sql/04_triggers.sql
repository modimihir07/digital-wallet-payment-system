-- =====================================================================
-- 04_triggers.sql : audit triggers (DB enforces traceability, not just Java)
-- Run: mysql -u root -p wallet_db < sql/04_triggers.sql
-- =====================================================================
USE wallet_db;

DROP TRIGGER IF EXISTS trg_wallet_balance_audit;
DROP TRIGGER IF EXISTS trg_transaction_status_audit;
DROP TRIGGER IF EXISTS trg_bill_paid;

DELIMITER $$

-- Audit every wallet balance/status change
CREATE TRIGGER trg_wallet_balance_audit
AFTER UPDATE ON wallets
FOR EACH ROW
BEGIN
    IF OLD.balance <> NEW.balance OR OLD.status <> NEW.status THEN
        INSERT INTO audit_log (table_name, action, record_id, old_value, new_value, changed_by)
        VALUES (
            'wallets',
            'UPDATE',
            NEW.wallet_id,
            JSON_OBJECT('balance', OLD.balance, 'status', OLD.status),
            JSON_OBJECT('balance', NEW.balance, 'status', NEW.status),
            NULL
        );
    END IF;
END$$

-- Audit every transaction status change
CREATE TRIGGER trg_transaction_status_audit
AFTER UPDATE ON transactions
FOR EACH ROW
BEGIN
    IF OLD.status <> NEW.status THEN
        INSERT INTO audit_log (table_name, action, record_id, old_value, new_value, changed_by)
        VALUES (
            'transactions',
            'STATUS_CHANGE',
            NEW.txn_id,
            JSON_OBJECT('status', OLD.status),
            JSON_OBJECT('status', NEW.status),
            NULL
        );
    END IF;
END$$

-- Audit bill payments
CREATE TRIGGER trg_bill_paid
AFTER UPDATE ON bills
FOR EACH ROW
BEGIN
    IF OLD.status <> NEW.status AND NEW.status = 'PAID' THEN
        INSERT INTO audit_log (table_name, action, record_id, old_value, new_value, changed_by)
        VALUES (
            'bills',
            'PAID',
            NEW.bill_id,
            JSON_OBJECT('status', OLD.status, 'paid_txn_id', OLD.paid_txn_id),
            JSON_OBJECT('status', NEW.status, 'paid_txn_id', NEW.paid_txn_id),
            NEW.user_id
        );
    END IF;
END$$

DELIMITER ;
