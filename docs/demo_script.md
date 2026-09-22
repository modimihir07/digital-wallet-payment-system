# Viva Demo Script (9 steps, ~10 minutes)

## 0. Setup (before the viva)

```bash
mysql -u root -p < sql/01_schema.sql
mysql -u root -p wallet_db < sql/03_procedures.sql
mysql -u root -p wallet_db < sql/04_triggers.sql
mysql -u root -p wallet_db < sql/05_views.sql
mysql -u root -p wallet_db < sql/02_seed.sql
mvn spring-boot:run
```

## 1. Show ER + schema.sql (1 min)

Open `docs/ER_diagram.md` and `sql/01_schema.sql`. Say: 9 tables in 3NF,
`wallets.user_id UNIQUE` enforces 1:1 ownership, `CHECK (balance >= 0)`,
`UNIQUE(idempotency_key)`, FKs everywhere, InnoDB for row locks.

## 2. Register 2 users (1 min)

```bash
curl -s -X POST localhost:8080/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"name":"Aarav","email":"aarav2@example.com","phone":"9111111111","password":"password123"}'
curl -s -X POST localhost:8080/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"name":"Diya","email":"diya2@example.com","phone":"9222222222","password":"password123"}'
```

Point out: one request creates user + wallet + USER role in ONE transaction
(AuthService). Password stored as BCrypt, never plaintext.

## 3. Transfer Rs.500, show txn + ledger + audit (2 min)

```bash
TOKEN=<aarav-jwt>
curl -s -X POST localhost:8080/api/wallet/topup \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"amount":2000.00}' 
curl -s -X POST localhost:8080/api/transfer \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -H 'X-Idempotency-Key: demo-key-001' \
  -d '{"fromWalletId":1,"toWalletId":2,"amount":500.00,"remarks":"lunch"}'
```

Then in MySQL:

```sql
SELECT * FROM transactions ORDER BY txn_id DESC LIMIT 1;
SELECT * FROM ledger_entries ORDER BY entry_id DESC LIMIT 2;
SELECT * FROM audit_log ORDER BY log_id DESC LIMIT 3;
```

Say: 1 txn row, 2 ledger rows (DEBIT/CREDIT with balance_after), audit rows
written by the TRIGGER — Java never inserts into audit_log.

## 4. Run ConcurrencyTest — money conserved (1.5 min)

```bash
mvn test -Dtest=ConcurrencyTest
```

Say: 100 threads each move Rs.10 A→B at the exact same instant. Final
A=9,000, B=1,000, total still 10,000. This only passes because of
`SELECT ... FOR UPDATE`.

## 5. Disable FOR UPDATE — show the bug (1.5 min)

Temporarily change `WalletDAO.findByIdForUpdate` to a plain SELECT (remove
`FOR UPDATE`), re-run the test: final A reads MORE than 9,000 — lost updates.
This is the classic read-modify-write race.

## 6. Re-enable — correct again (30 sec)

Restore `FOR UPDATE`, re-run: green again. One keyword is the difference
between a bank and a bug.

## 7. EXPLAIN before/after index (1 min)

```sql
EXPLAIN SELECT * FROM transactions WHERE from_wallet = 1;
```

Indexes `idx_from_wallet / idx_to_wallet / idx_created_at / idx_status`
already exist in schema.sql — show `key` column goes from NULL (full scan)
to the index name after adding them (drop one index live to demo).

## 8. Show v_daily_summary (30 sec)

```sql
SELECT * FROM v_daily_summary;
SELECT * FROM v_top_users LIMIT 5;
SELECT * FROM v_pending_bills;
```

Say: reports are DB views, so every client sees identical numbers.

## 9. Show audit_log (30 sec)

```sql
SELECT log_id, table_name, action, old_value, new_value, changed_at
FROM audit_log ORDER BY log_id DESC LIMIT 5;
```

Say: triggers fire on every balance/status change — even a manual
`UPDATE wallets ...` in the console leaves a trail. Java is not the only
source of correctness.
