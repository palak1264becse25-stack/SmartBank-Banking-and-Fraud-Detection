# SmartBank – Integrated Core Java Project

This project combines the three uploaded team modules (authentication, MySQL banking operations, and fraud detection) with the account model and DAO layer reconstructed from the user's earlier SmartBank work.

## Requirements
- JDK 25 (the Maven compiler release is set to 25)
- MySQL Server
- Maven 3.9+
- IntelliJ IDEA

## Configure MySQL
1. Start MySQL Server.
2. Open `src/main/resources/db.properties` and set `db.user` and `db.password` to your local MySQL credentials.
3. Run `database/schema.sql` in MySQL Workbench or the MySQL CLI. The application also creates the core banking tables when it connects and adds the `user_id` and `interest_rate` compatibility columns to an existing `accounts` table.
4. The schema seeds test accounts `1001`, `1002`, and `1003` for the banking-operations demo.

The unified database is named `bank_db`. The reconstructed DAO's original `createAccount(int, String, BigDecimal, BigDecimal)` API now writes to this shared `accounts` table, alongside the submitted banking-operations module.

## Run
From the project root:

```bash
mvn clean compile
mvn exec:java
```

In IntelliJ, open the folder containing `pom.xml` as a Maven project, reload Maven, wait for dependencies to finish downloading, and run `Main.java`.

## Integrated flow
- `Main.java`: registration, login, MySQL connection test.
- `UserDashboard.java`: authenticated customer menu.
- Banking operations: deposit, withdrawal, transfer, balance inquiry, and transaction history.
- Fraud detection: successful deposits, withdrawals, and transfers are passed to the fraud rules engine during the current application session.
- Original account layer: `models.user`, `models.Account`, and `dao.AccountDAO` are included. `AccountDAO` provides account creation, existence checks, balance retrieval, balance updates, and account lookup.

## Important limitations to understand before evaluation
- The submitted authentication module stores credentials and login sessions in memory. A newly registered customer's bank account is created in MySQL, but the login credential itself is not persisted; registered logins are lost when the application exits.
- Fraud alerts and transaction-monitoring state are currently in memory. The fraud review dashboard includes the supplied demo workflow; it is not a persistent fraud-case management system.
- The banking-operations module and reconstructed account DAO share the `bank_db.accounts` table. Avoid using `AccountDAO.updateBalance` for normal money movement; use deposit, withdrawal, and transfer operations so transaction records are written.
- MySQL credentials are environment-specific. Database connectivity and live transactions must be tested on the local machine after configuration.

## Project structure
- `src/main/java/` – application, authentication, and banking operations
- `src/main/java/models/` – reconstructed `user` and `Account` domain models
- `src/main/java/dao/` – reconstructed JDBC `AccountDAO`
- `src/main/java/com/smartbank/fraud/` – fraud detection module
- `src/main/resources/db.properties` – MySQL configuration
- `database/schema.sql` – unified database schema and sample accounts
