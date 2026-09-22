# Architecture — Digital Wallet & Payment System

## Component diagram

```mermaid
flowchart LR
    Client["Client: Postman / curl / Frontend"] --> AC["AuthController<br/>/api/auth/*"]
    Client --> WC["WalletController<br/>/api/wallet/*"]
    Client --> TC["TransferController<br/>/api/transfer"]
    Client --> BC["BillController<br/>/api/bills/*"]
    Client --> ADC["AdminController<br/>/api/admin/*"]

    AC --> AS["AuthService"]
    WC --> WS["WalletService"]
    TC --> TS["TransferService (CORE)"]
    BC --> BS["BillService"]
    ADC --> AUS["AuditService"]

    AS --> UD["UserDAO"]
    AS --> WD["WalletDAO"]
    WS --> WD
    WS --> TD["TransactionDAO"]
    WS --> LD["LedgerDAO"]
    TS --> WD
    TS --> TD
    TS --> LD
    BS --> BD["BillDAO"]
    BS --> WD
    BS --> TD
    BS --> LD
    AUS --> AUD["AuditDAO"]
    AUS --> WD

    AS & WS & TS & BS & AUS --> TX["TxManager<br/>begin / commit / rollback"]
    TX --> HIK["HikariCP pool<br/>min 5 / max 20"]
    HIK --> DB[("MySQL 8<br/>wallet_db<br/>constraints + triggers<br/>+ procedures + views")]

    JWT["JwtAuthFilter<br/>Bearer check<br/>ROLE_ADMIN gate"] -. guards .-> WC & TC & BC & ADC
```

Layer rules: Controllers = HTTP only. Services = own transaction boundaries + business rules.
DAOs = raw JDBC PreparedStatements only, no business logic, connection passed in by caller.

## Transfer sequence (with row-level locking)

```mermaid
sequenceDiagram
    participant C as Client
    participant F as JwtAuthFilter
    participant TC as TransferController
    participant TS as TransferService
    participant TX as TxManager
    participant DB as MySQL (InnoDB)

    C->>F: POST /api/transfer + Bearer JWT + X-Idempotency-Key
    F->>F: verify JWT
    F->>TC: forward (authUserId)
    TC->>TS: transfer(req)
    TS->>TX: execute(callback)
    TX->>DB: BEGIN (autoCommit=false)
    TS->>DB: SELECT txn WHERE idempotency_key=? (replay? return)
    TS->>DB: SELECT * FROM wallets WHERE id=? FOR UPDATE (lock A)
    TS->>DB: SELECT * FROM wallets WHERE id=? FOR UPDATE (lock B)
    Note over TS,DB: concurrent transfers on same wallets now serialise
    TS->>TS: validate ACTIVE + balance >= amount (after lock)
    TS->>DB: UPDATE wallets SET balance=balance-? (from)
    TS->>DB: UPDATE wallets SET balance=balance+? (to)
    TS->>DB: INSERT INTO transactions (UNIQUE key backstop)
    TS->>DB: INSERT INTO ledger_entries x2 (DEBIT + CREDIT)
    TX->>DB: COMMIT
    DB-->>DB: trg_wallet_balance_audit fires -> audit_log
    TS-->>C: 200 { txnId, SUCCESS }
```

On any exception: TxManager rolls back, GlobalExceptionHandler maps to
400 / 403 / 404 / 409 JSON.
