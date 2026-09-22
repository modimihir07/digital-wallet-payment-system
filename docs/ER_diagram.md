# ER Diagram — Digital Wallet & Payment System

```mermaid
erDiagram
    users ||--o| wallets : owns
    users ||--o{ user_roles : has
    roles ||--o{ user_roles : grants
    users ||--o{ bills : owes
    merchants ||--o{ bills : issues
    merchants }o--o| wallets : settles_to
    wallets ||--o{ transactions : "from_wallet" 
    wallets ||--o{ transactions : "to_wallet"
    transactions ||--o{ ledger_entries : produces
    wallets ||--o{ ledger_entries : records
    transactions }o--o| bills : pays
    wallets ||--o{ audit_log : tracked_in
    transactions ||--o{ audit_log : tracked_in
    bills ||--o{ audit_log : tracked_in

    users {
        BIGINT user_id PK
        VARCHAR name
        VARCHAR email UK
        VARCHAR phone UK
        VARCHAR password_hash
        ENUM status
        TIMESTAMP created_at
    }
    wallets {
        BIGINT wallet_id PK
        BIGINT user_id UK_FK
        DECIMAL balance
        CHAR currency
        ENUM status
        TIMESTAMP created_at
    }
    transactions {
        BIGINT txn_id PK
        BIGINT from_wallet FK
        BIGINT to_wallet FK
        DECIMAL amount
        ENUM type
        ENUM status
        VARCHAR idempotency_key UK
        VARCHAR remarks
        TIMESTAMP created_at
    }
    ledger_entries {
        BIGINT entry_id PK
        BIGINT txn_id FK
        BIGINT wallet_id FK
        ENUM entry_type
        DECIMAL amount
        DECIMAL balance_after
        TIMESTAMP created_at
    }
    merchants {
        BIGINT merchant_id PK
        VARCHAR name
        VARCHAR category
        BIGINT wallet_id FK
        TIMESTAMP created_at
    }
    bills {
        BIGINT bill_id PK
        BIGINT user_id FK
        BIGINT merchant_id FK
        DECIMAL amount
        ENUM status
        DATE due_date
        BIGINT paid_txn_id FK
        TIMESTAMP created_at
    }
    audit_log {
        BIGINT log_id PK
        VARCHAR table_name
        VARCHAR action
        BIGINT record_id
        JSON old_value
        JSON new_value
        BIGINT changed_by
        TIMESTAMP changed_at
    }
    roles {
        INT role_id PK
        VARCHAR name UK
    }
    user_roles {
        BIGINT user_id PK_FK
        INT role_id PK_FK
    }
```

## Relationship notes

- `users 1:1 wallets` — enforced by `UNIQUE(user_id)` on wallets; wallet auto-created at registration.
- `transactions.from_wallet` NULL = external top-up source; `to_wallet` NULL = external sink (merchant without wallet).
- `ledger_entries` is append-only: exactly 2 rows per P2P (DEBIT + CREDIT), 1 row per top-up side.
- `audit_log` is written by DB triggers, never by Java — tamper-evident trail.
- `transactions.idempotency_key UNIQUE` is the final backstop against double-submit races.
