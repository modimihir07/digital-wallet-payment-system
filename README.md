# Digital Wallet & Payment System

[![Java](https://img.shields.io/badge/Java-17-ED8B00?style=flat-square&logo=openjdk&logoColor=white)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.5-6DB33F?style=flat-square&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1?style=flat-square&logo=mysql&logoColor=white)](https://www.mysql.com/)
[![HikariCP](https://img.shields.io/badge/HikariCP-Connection%20Pool-005571?style=flat-square)](https://github.com/brettwooldridge/HikariCP)
[![JWT](https://img.shields.io/badge/JWT-jjwt%200.12.5-black?style=flat-square&logo=jsonwebtokens)](https://jwt.io/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg?style=flat-square)](LICENSE)

A reliable, concurrent digital wallet backend and web application built with **Java 17**, **Spring Boot 3**, and **MySQL 8**. 

This repository was created as an academic capstone for a **Database Management Systems (DBMS)** college course. Rather than relying on heavy ORMs that hide how queries execute, the project uses **raw JDBC** and **HikariCP** so that transaction boundaries, row-level locks, and constraints are explicitly defined and controlled. It also includes a responsive single-page web frontend served directly by Spring Boot.

---

## Table of Contents
- [Project Overview](#project-overview)
- [Web Interface & Features](#web-interface--features)
- [Academic Context & DBMS Concepts](#academic-context--dbms-concepts)
- [Architecture & Design](#architecture--design)
- [Database Schema](#database-schema)
- [Concurrency & Deadlock Prevention](#concurrency--deadlock-prevention)
- [API Endpoints](#api-endpoints)
- [Database Setup Scripts](#database-setup-scripts)
- [Demo Credentials](#demo-credentials)
- [Local Installation & Setup](#local-installation--setup)
- [Running the Tests](#running-the-tests)
- [Postman Collection](#postman-collection)
- [License](#license)

---

## Project Overview

In financial applications, software crashes and network retries are routine. If two users send each other money at the exact same moment, or a user rapidly double-clicks "Pay", the system must remain completely accurate. 

This project solves these real-world challenges through:
- **Pessimistic row-level locking (`SELECT ... FOR UPDATE`)**: Eliminates lost updates under high concurrency.
- **Ordered lock acquisition**: Prevents cyclic wait deadlocks during concurrent bidirectional transfers.
- **Strict idempotency**: Every payment and transfer supports an idempotency key with a database-level `UNIQUE` constraint.
- **Double-entry ledger**: Every transfer produces two non-destructive, append-only journal entries (`DEBIT` and `CREDIT`) with snapshot balances.
- **Database triggers for auditing**: Automatic `AFTER UPDATE` triggers record all balance and status changes to an `audit_log` table in JSON format.
- **Analytical views**: Precomputed database views calculate user statements, daily transaction volumes, and top spenders directly inside MySQL.
- **Integrated Web UI**: A modern, lightweight frontend served directly out of Spring Boot with no build steps or external dependencies.

---

## Web Interface & Features

The project includes a single-page web frontend built with vanilla HTML5, CSS3, and ES6+ JavaScript. It lives inside `src/main/resources/static/` and is served directly at `http://localhost:8080/`.

- **Fintech Aesthetic**: Dark mode by default with an instant Light mode toggle (saved to `localStorage`), subtle glassmorphism cards, and responsive layouts.
- **Authentication**: JWT-based login and registration flows with client-side form validation and role detection.
- **Interactive Dashboard**: Real-time balance display with count-up animation, a 7-day spending trends line chart powered by Chart.js, quick actions (Top-Up modal), and recent ledger activity.
- **P2P Money Transfers**: Peer-to-peer transfers with client-generated idempotency keys (`crypto.randomUUID()`), client-side balance validation, and instant modal transaction receipts.
- **Bill Payments**: Searchable directory of utility bills with category filters (`All`, `Unpaid`, `Paid`) and atomic one-click bill settlement.
- **Account Statements**: Paginated double-entry ledger history with search and transaction type filters, plus one-click CSV statement export.
- **Admin Management Console**: Role-protected portal (`ROLE_ADMIN`) featuring wallet freeze/unfreeze controls, real-time database trigger audit logs, platform transaction volume charts, and top user leaderboards.

---

## Academic Context & DBMS Concepts

Many university software projects treat the database simply as passive table storage through frameworks like Spring Data JPA. This project was built to practically implement the core computer science principles taught in Database Management Systems:

| Theoretical DBMS Topic | Real-World Problem | Implementation in this Project | Key Code / SQL File |
|---|---|---|---|
| **ACID Transactions** | Partial transfer if a crash occurs mid-flight | All multi-step operations execute inside a single transaction with explicit commit/rollback handling | [`TxManager.java`](src/main/java/com/wallet/util/TxManager.java) |
| **Pessimistic Locking** | Lost-update anomalies during concurrent debits | `SELECT ... FOR UPDATE` acquires row locks so only one thread modifies a wallet at a time | [`WalletDAO.java`](src/main/java/com/wallet/dao/WalletDAO.java) |
| **Deadlock Avoidance** | Mutual blocking ($A \rightarrow B$ vs $B \rightarrow A$) causing MySQL Error 1213 | Resource ordering protocol: always lock the smaller `wallet_id` first | [`TransferService.java`](src/main/java/com/wallet/service/TransferService.java) |
| **Relational Normalization** | Redundancy and update anomalies | 9 normalized relational tables conforming to Third Normal Form (3NF) | [`sql/01_schema.sql`](sql/01_schema.sql) |
| **Integrity Constraints** | Negative balances or duplicate transactions | Table-level `CHECK (balance >= 0)`, `UNIQUE(user_id)`, and `UNIQUE(idempotency_key)` | [`sql/01_schema.sql`](sql/01_schema.sql) |
| **Active Subsystems (Triggers)** | Tamper-evident logging even if Java code is bypassed | `AFTER UPDATE` triggers log old and new values as JSON snapshots into `audit_log` | [`sql/04_triggers.sql`](sql/04_triggers.sql) |
| **Stored Procedures** | Database-side atomic operations | Standalone MySQL procedures for transfer, top-up, and bill pay with `SQLEXCEPTION` handlers | [`sql/03_procedures.sql`](sql/03_procedures.sql) |
| **Database Views** | Complex queries cluttering application code | `v_daily_summary`, `v_top_users`, `v_user_statement`, and `v_pending_bills` | [`sql/05_views.sql`](sql/05_views.sql) |

---

## Architecture & Design

The application follows a clean layered design where each tier has a distinct, single responsibility:

```
Client (Web Browser / Postman)
         │
         ▼
JwtAuthFilter (Checks Bearer JWT & validates roles)
         │
         ▼
Controllers (Handles HTTP requests, inputs, and status codes)
         │
         ▼
Services (Owns business logic and transaction boundaries)
         │
         ▼
TxManager (Manages database connection auto-commit, commit, and rollback)
         │
         ▼
DAOs (Pure JDBC PreparedStatements, zero business logic)
         │
         ▼
HikariCP Connection Pool
         │
         ▼
MySQL 8 Database (InnoDB engine, row locks, triggers, views, procedures)
```

- **Controller Layer**: Parses input, returns clean HTTP responses, and handles exceptions via `GlobalExceptionHandler`.
- **Service Layer**: Manages business logic and controls transaction boundaries using `TxManager`.
- **DAO Layer**: Executes raw JDBC `PreparedStatement`s using the active `Connection` provided by the caller. No auto-commit leaks or hidden queries.

---

## Database Schema

The database consists of 9 normalized tables:

- `users`: User credentials, status, and registration timestamp.
- `wallets`: 1:1 linked to each user via `UNIQUE(user_id)`. Enforces `CHECK (balance >= 0)`.
- `transactions`: Records all transfer headers (P2P, TOPUP, BILL, REFUND) with `UNIQUE(idempotency_key)`.
- `ledger_entries`: Append-only accounting entries (`DEBIT` / `CREDIT`) storing the `balance_after`.
- `merchants`: Registered billers (e.g. utility, telecom) with an optional wallet reference.
- `bills`: Invoices issued to users with statuses (`UNPAID`, `PAID`, `OVERDUE`).
- `audit_log`: Chronological change log populated automatically by database triggers.
- `roles` & `user_roles`: Simple role-based authorization model (`USER`, `ADMIN`).

An Entity-Relationship (ER) diagram and detailed relationship notes are documented in [`docs/ER_diagram.md`](docs/ER_diagram.md).

---

## Concurrency & Deadlock Prevention

The core money transfer workflow in [`TransferService.java`](src/main/java/com/wallet/service/TransferService.java) executes as follows:

1. **Idempotency check**: If a transfer with this `idempotency_key` already succeeded, immediately return the saved transaction ID without processing again.
2. **Deterministic lock ordering**:
   ```java
   long firstLock  = Math.min(fromWalletId, toWalletId);
   long secondLock = Math.max(fromWalletId, toWalletId);
   walletDAO.findByIdForUpdate(conn, firstLock);
   walletDAO.findByIdForUpdate(conn, secondLock);
   ```
   If User 1 transfers to User 2 at the same time User 2 transfers to User 1, both concurrent transactions request Lock #1 first, then Lock #2. Because both wait in the same direction, a cyclic dependency cannot form, making deadlocks mathematically impossible.
3. **Post-lock validation**: Check whether both wallets are active and if the sender has sufficient balance *after* the locks are acquired. This eliminates Time-Of-Check to Time-Of-Use (TOCTOU) race conditions.
4. **Balance update**: Atomically increment/decrement the wallet rows.
5. **Insert transaction**: Insert the transaction header. The database `UNIQUE(idempotency_key)` acts as a final safety check.
6. **Append ledger rows**: Write one `DEBIT` and one `CREDIT` record with the resulting account balances.
7. **Commit & Audit**: The transaction commits, and database triggers automatically write the state changes to `audit_log`.

---

## API Endpoints

Swagger UI documentation is available at `http://localhost:8080/swagger-ui.html` when the application is running.

### Authentication (`/api/auth`)
| Method | Path | Auth Required | Description |
|---|---|---|---|
| `POST` | `/api/auth/register` | No | Creates a user account, automatically provisions an active wallet, and assigns `USER` role |
| `POST` | `/api/auth/login` | No | Verifies credentials and returns a JWT token |

### Wallet (`/api/wallet`)
| Method | Path | Auth Required | Description |
|---|---|---|---|
| `GET` | `/api/wallet/me` | User | Fetches the logged-in user's wallet profile and current balance |
| `GET` | `/api/wallet/me/statement` | User | Returns paginated ledger statement (`page`, `size`) |
| `POST` | `/api/wallet/topup` | User | Adds money to wallet (simulated gateway top-up) |

### Transfers & Bills (`/api/transfer`, `/api/bills`)
| Method | Path | Auth Required | Description |
|---|---|---|---|
| `POST` | `/api/transfer` | User | Executes an atomic P2P transfer (requires `X-Idempotency-Key` header) |
| `GET` | `/api/transfer/{txnId}` | User | Retrieves details of a specific transaction |
| `GET` | `/api/bills/my` | User | Lists unpaid and paid bills for the current user |
| `POST` | `/api/bills/{billId}/pay` | User | Pays an outstanding merchant bill from wallet balance |

### Admin Operations (`/api/admin`)
| Method | Path | Auth Required | Description |
|---|---|---|---|
| `GET` | `/api/admin/wallets` | Admin | Lists all wallets across the system |
| `POST` | `/api/admin/wallets/{id}/freeze` | Admin | Freezes a wallet, blocking all transactions |
| `POST` | `/api/admin/wallets/{id}/unfreeze` | Admin | Restores a wallet back to `ACTIVE` status |
| `GET` | `/api/admin/audit` | Admin | Views trigger-generated audit logs |
| `GET` | `/api/admin/reports/daily` | Admin | Returns daily transaction counts and volume summary |
| `GET` | `/api/admin/reports/top-users` | Admin | Ranks users by outgoing transaction volume |

---

## Database Setup Scripts

The SQL scripts in the [`sql/`](sql/) folder should be executed in order:

```bash
# 1. Create database and tables
mysql -u root -p < sql/01_schema.sql

# 2. Stored procedures
mysql -u root -p wallet_db < sql/03_procedures.sql

# 3. Audit triggers
mysql -u root -p wallet_db < sql/04_triggers.sql

# 4. Reporting views
mysql -u root -p wallet_db < sql/05_views.sql

# 5. Base seed data (initial test users and roles)
mysql -u root -p wallet_db < sql/02_seed.sql

# 6. Realistic demo dataset (15 users, 8 merchants, 40 bills, 100 txns, 200 ledger rows)
mysql -u root -p wallet_db < sql/06_seed_demo_data.sql
```

---

## Demo Credentials

If you load [`sql/06_seed_demo_data.sql`](sql/06_seed_demo_data.sql), the following pre-configured accounts are available out of the box:

| Role | Name | Email | Password | Initial Balance |
|---|---|---|---|---|
| **Admin** | Rohan Sharma | `rohan.sharma@example.com` | `Password@123` | ₹25,000.00 |
| **Admin** | Priya Verma | `priya.verma@example.com` | `Password@123` | ₹18,500.00 |
| **User** | Vikram Singh | `vikram.singh@example.com` | `Password@123` | ₹12,000.00 |
| **User** | Sneha Deshmukh | `sneha.deshmukh@example.com` | `Password@123` | ₹8,450.00 |
| **User** | Ananya Iyer | `ananya.iyer@example.com` | `Password@123` | ₹15,750.00 |

*(All 15 seed accounts use the default password `Password@123`)*

---

## Local Installation & Setup

### Prerequisites
- **Java 17** or higher
- **Maven 3.8+**
- **MySQL 8.0+**

### Configuration
Set database and JWT parameters via environment variables (or leave default values from `src/main/resources/application.yml` for local development):

```bash
export DB_URL="jdbc:mysql://localhost:3306/wallet_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
export DB_USERNAME="wallet_user"
export DB_PASSWORD="wallet_password"
export JWT_SECRET="your-256-bit-secret-key-here-must-be-very-long-and-secure"
```

### Build and Run
```bash
# Compile and package application
mvn clean package -DskipTests

# Start the Spring Boot application
mvn spring-boot:run
```

Once started, open `http://localhost:8080` in any web browser to access the frontend application, or explore the REST endpoints via Swagger at `http://localhost:8080/swagger-ui.html`.

---

## Running the Tests

The project includes unit, DAO, and concurrency tests. The tests run against an **in-memory H2 database** in MySQL mode, so a running MySQL instance is not required to execute them.

```bash
# Run all tests
mvn test

# Run the 100-thread concurrency test specifically
mvn test -Dtest=ConcurrencyTest
```

### Concurrency Test Verification
[`ConcurrencyTest.java`](src/test/java/com/wallet/ConcurrencyTest.java) creates 100 concurrent worker threads that fire simultaneous ₹10 transfers from Account A (initial ₹10,000) to Account B (initial ₹0) using a synchronization barrier:
- **With `FOR UPDATE` enabled**: All 100 transactions serialize correctly without lost updates. Account A ends at ₹9,000, Account B ends at ₹1,000, and the total ₹10,000 is perfectly conserved.
- **Without `FOR UPDATE`**: Simultaneous uncoordinated reads cause lost updates where concurrent threads overwrite each other's debits, resulting in balance drift ($A > 9,000$).

---

## Postman Collection

A ready-to-use Postman collection is included in [`postman/wallet_collection.json`](postman/wallet_collection.json). It contains pre-configured requests for registration, login, transfers, bill payments, statements, and administrative reports. The collection automatically captures JWT tokens upon login and passes them to subsequent requests.

---

## License

This project is open-source software licensed under the [MIT License](LICENSE).
