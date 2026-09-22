-- =====================================================================
-- 03_procedures.sql : atomic stored procedures with row-level locking
-- Run: mysql -u root -p wallet_db < sql/03_procedures.sql
-- =====================================================================
USE wallet_db;

DROP PROCEDURE IF EXISTS sp_transfer_money;
DROP PROCEDURE IF EXISTS sp_topup;
DROP PROCEDURE IF EXISTS sp_pay_bill;

DELIMITER $$

-- ---------------------------------------------------------------------
-- sp_transfer_money : atomic P2P transfer with SELECT ... FOR UPDATE
-- ---------------------------------------------------------------------
CREATE PROCEDURE sp_transfer_money(
    IN p_from_wallet BIGINT,
    IN p_to_wallet BIGINT,
    IN p_amount DECIMAL(15,2),
    IN p_idem_key VARCHAR(64),
    OUT p_txn_id BIGINT,
    OUT p_status VARCHAR(20)
)
proc_block: BEGIN
    DECLARE v_from_balance DECIMAL(15,2);
    DECLARE v_from_status VARCHAR(20);
    DECLARE v_to_status VARCHAR(20);
    DECLARE v_existing BIGINT DEFAULT NULL;
    DECLARE v_existing_status VARCHAR(20) DEFAULT NULL;
    DECLARE v_from_new DECIMAL(15,2);
    DECLARE v_to_new DECIMAL(15,2);

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        SET p_txn_id = NULL;
        SET p_status = 'FAILED';
        RESIGNAL;
    END;

    IF p_amount IS NULL OR p_amount <= 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Amount must be greater than zero';
    END IF;

    IF p_from_wallet = p_to_wallet THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Source and destination wallets must differ';
    END IF;

    START TRANSACTION;

    -- Idempotency: return existing SUCCESS txn
    IF p_idem_key IS NOT NULL THEN
        SELECT txn_id, status INTO v_existing, v_existing_status
        FROM transactions WHERE idempotency_key = p_idem_key LIMIT 1;
        IF v_existing IS NOT NULL THEN
            COMMIT;
            SET p_txn_id = v_existing;
            SET p_status = v_existing_status;
            LEAVE proc_block;
        END IF;
    END IF;

    -- Row-level locks (order not critical for 2 rows, but lock both)
    SELECT balance, status INTO v_from_balance, v_from_status
    FROM wallets WHERE wallet_id = p_from_wallet FOR UPDATE;
    IF v_from_balance IS NULL THEN
        ROLLBACK;
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Source wallet not found';
    END IF;

    SELECT status, balance INTO v_to_status, v_to_new
    FROM wallets WHERE wallet_id = p_to_wallet FOR UPDATE;
    IF v_to_status IS NULL THEN
        ROLLBACK;
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Destination wallet not found';
    END IF;

    IF v_from_status <> 'ACTIVE' THEN
        ROLLBACK;
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Source wallet is not ACTIVE';
    END IF;
    IF v_to_status <> 'ACTIVE' THEN
        ROLLBACK;
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Destination wallet is not ACTIVE';
    END IF;
    IF v_from_balance < p_amount THEN
        ROLLBACK;
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Insufficient balance';
    END IF;

    INSERT INTO transactions (from_wallet, to_wallet, amount, type, status, idempotency_key)
    VALUES (p_from_wallet, p_to_wallet, p_amount, 'P2P', 'SUCCESS', p_idem_key);
    SET p_txn_id = LAST_INSERT_ID();

    UPDATE wallets SET balance = balance - p_amount WHERE wallet_id = p_from_wallet;
    UPDATE wallets SET balance = balance + p_amount WHERE wallet_id = p_to_wallet;

    SELECT balance INTO v_from_new FROM wallets WHERE wallet_id = p_from_wallet;
    SELECT balance INTO v_to_new FROM wallets WHERE wallet_id = p_to_wallet;

    INSERT INTO ledger_entries (txn_id, wallet_id, entry_type, amount, balance_after)
    VALUES (p_txn_id, p_from_wallet, 'DEBIT', p_amount, v_from_new);
    INSERT INTO ledger_entries (txn_id, wallet_id, entry_type, amount, balance_after)
    VALUES (p_txn_id, p_to_wallet, 'CREDIT', p_amount, v_to_new);

    COMMIT;
    SET p_status = 'SUCCESS';
END$$

-- ---------------------------------------------------------------------
-- sp_topup : simulated money add (external source -> wallet)
-- ---------------------------------------------------------------------
CREATE PROCEDURE sp_topup(
    IN p_wallet_id BIGINT,
    IN p_amount DECIMAL(15,2),
    IN p_idem_key VARCHAR(64),
    OUT p_txn_id BIGINT
)
topup_block: BEGIN
    DECLARE v_status VARCHAR(20);
    DECLARE v_existing BIGINT DEFAULT NULL;
    DECLARE v_new_balance DECIMAL(15,2);

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    IF p_amount IS NULL OR p_amount <= 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Top-up amount must be greater than zero';
    END IF;

    START TRANSACTION;

    IF p_idem_key IS NOT NULL THEN
        SELECT txn_id INTO v_existing FROM transactions
        WHERE idempotency_key = p_idem_key LIMIT 1;
        IF v_existing IS NOT NULL THEN
            COMMIT;
            SET p_txn_id = v_existing;
            LEAVE topup_block;
        END IF;
    END IF;

    SELECT status INTO v_status FROM wallets WHERE wallet_id = p_wallet_id FOR UPDATE;
    IF v_status IS NULL THEN
        ROLLBACK;
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Wallet not found';
    END IF;
    IF v_status <> 'ACTIVE' THEN
        ROLLBACK;
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Wallet is not ACTIVE';
    END IF;

    INSERT INTO transactions (from_wallet, to_wallet, amount, type, status, idempotency_key, remarks)
    VALUES (NULL, p_wallet_id, p_amount, 'TOPUP', 'SUCCESS', p_idem_key, 'Simulated top-up');
    SET p_txn_id = LAST_INSERT_ID();

    UPDATE wallets SET balance = balance + p_amount WHERE wallet_id = p_wallet_id;
    SELECT balance INTO v_new_balance FROM wallets WHERE wallet_id = p_wallet_id;

    INSERT INTO ledger_entries (txn_id, wallet_id, entry_type, amount, balance_after)
    VALUES (p_txn_id, p_wallet_id, 'CREDIT', p_amount, v_new_balance);

    COMMIT;
END$$

-- ---------------------------------------------------------------------
-- sp_pay_bill : pay an UNPAID bill from the user's wallet
-- ---------------------------------------------------------------------
CREATE PROCEDURE sp_pay_bill(
    IN p_bill_id BIGINT,
    IN p_idem_key VARCHAR(64),
    OUT p_txn_id BIGINT
)
pay_block: BEGIN
    DECLARE v_bill_status VARCHAR(20);
    DECLARE v_bill_amount DECIMAL(15,2);
    DECLARE v_user_id BIGINT;
    DECLARE v_merchant_wallet BIGINT;
    DECLARE v_payer_wallet BIGINT;
    DECLARE v_payer_balance DECIMAL(15,2);
    DECLARE v_payer_status VARCHAR(20);
    DECLARE v_existing BIGINT DEFAULT NULL;
    DECLARE v_new_balance DECIMAL(15,2);

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    START TRANSACTION;

    IF p_idem_key IS NOT NULL THEN
        SELECT txn_id INTO v_existing FROM transactions
        WHERE idempotency_key = p_idem_key LIMIT 1;
        IF v_existing IS NOT NULL THEN
            COMMIT;
            SET p_txn_id = v_existing;
            LEAVE pay_block;
        END IF;
    END IF;

    SELECT status, amount, user_id INTO v_bill_status, v_bill_amount, v_user_id
    FROM bills WHERE bill_id = p_bill_id FOR UPDATE;
    IF v_bill_status IS NULL THEN
        ROLLBACK;
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Bill not found';
    END IF;
    IF v_bill_status = 'PAID' THEN
        ROLLBACK;
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Bill already paid';
    END IF;

    SELECT wallet_id, balance, status INTO v_payer_wallet, v_payer_balance, v_payer_status
    FROM wallets WHERE user_id = v_user_id FOR UPDATE;
    IF v_payer_wallet IS NULL THEN
        ROLLBACK;
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Payer wallet not found';
    END IF;
    IF v_payer_status <> 'ACTIVE' THEN
        ROLLBACK;
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Payer wallet is not ACTIVE';
    END IF;
    IF v_payer_balance < v_bill_amount THEN
        ROLLBACK;
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Insufficient balance to pay bill';
    END IF;

    -- Merchant wallet: bills -> merchants -> wallet_id (may be NULL; then credit stays external)
    SELECT m.wallet_id INTO v_merchant_wallet
    FROM bills b JOIN merchants m ON m.merchant_id = b.merchant_id
    WHERE b.bill_id = p_bill_id;

    INSERT INTO transactions (from_wallet, to_wallet, amount, type, status, idempotency_key, remarks)
    VALUES (v_payer_wallet, v_merchant_wallet, v_bill_amount, 'BILL', 'SUCCESS', p_idem_key,
            CONCAT('Bill payment #', p_bill_id));
    SET p_txn_id = LAST_INSERT_ID();

    UPDATE wallets SET balance = balance - v_bill_amount WHERE wallet_id = v_payer_wallet;
    SELECT balance INTO v_new_balance FROM wallets WHERE wallet_id = v_payer_wallet;
    INSERT INTO ledger_entries (txn_id, wallet_id, entry_type, amount, balance_after)
    VALUES (p_txn_id, v_payer_wallet, 'DEBIT', v_bill_amount, v_new_balance);

    IF v_merchant_wallet IS NOT NULL THEN
        UPDATE wallets SET balance = balance + v_bill_amount WHERE wallet_id = v_merchant_wallet;
        SELECT balance INTO v_new_balance FROM wallets WHERE wallet_id = v_merchant_wallet;
        INSERT INTO ledger_entries (txn_id, wallet_id, entry_type, amount, balance_after)
        VALUES (p_txn_id, v_merchant_wallet, 'CREDIT', v_bill_amount, v_new_balance);
    END IF;

    UPDATE bills SET status = 'PAID', paid_txn_id = p_txn_id WHERE bill_id = p_bill_id;

    COMMIT;
END$$

DELIMITER ;
