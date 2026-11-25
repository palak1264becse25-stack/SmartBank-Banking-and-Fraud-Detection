# Member 3: Banking Operations Developer
## Core Banking System – Operations Module

This project contains the implementation for **Member 3 – Banking Operations**, developing the core banking functionality from the application user's perspective.

---

## Deliverables Summary

| File | Task | Key Functionality |
| :--- | :--- | :--- |
| **`Deposit.java`** | Task 1: Deposit Money | Validates positive deposit amount, updates database balance, logs transaction with unique ID, prints confirmation slip. |
| **`Withdraw.java`** | Task 2: Withdraw Money | Checks live database balance, prevents overdraft/insufficient balance, updates balance, records transaction, displays receipt or error. |
| **`BalanceInquiry.java`** | Task 3: Check Account Balance | Retrieves live balance directly from database SQL query on every call (no stale local variable cache), displays full account info. |
| **`MoneyTransfer.java`** | Task 4: Money Transfer | Verifies recipient account exists, checks sender balance, executes atomic dual debit/credit with commit/rollback (ACID), logs both transactions. |
| **`TransactionHistory.java`** | Task 5: Transaction History | Queries and displays all past transactions in a formatted ASCII statement (ID, Type, Amount, Timestamp, Status, Details). |
| **`DatabaseConnection.java`** | Supporting Infrastructure | Centralized JDBC connection manager; reads `db.properties` and auto-initializes tables and seed accounts if needed. |
| **`BankAppMenu.java`** | Integrated Dashboard | Interactive console menu integrating all 5 tasks for unified testing and demonstration. |
| **`schema.sql`** | Database Schema | SQL script to create `bank_db`, `accounts` table, `transactions` table, and test seed records. |
| **`db.properties`** | Configuration File | Database connection properties (driver, URL, username, password). |

---

## Detailed Task Implementations

### Task 1: Deposit Money (`Deposit.java`)
- **Amount Validation:** Verifies that `amount > 0`. If `<= 0`, rejects the operation immediately with an error.
- **Account Verification:** Checks that the account exists and has status `ACTIVE`.
- **Database Balance Update:** Executes `UPDATE accounts SET balance = balance + ? WHERE account_number = ?`.
- **Transaction Logging:** Inserts record into `transactions` table with:
  - `transaction_id`: Unique generated identifier (e.g. `DEP-171630...-A1B2`)
  - `account_number`: Target account
  - `transaction_type`: `DEPOSIT`
  - `amount`: Deposit amount
  - `date_time`: Current timestamp
  - `status`: `SUCCESS`
- **Confirmation Display:** Generates a structured deposit receipt with previous balance, deposited amount, new balance, and timestamp.

### Task 2: Withdraw Money (`Withdraw.java`)
- **Amount Validation:** Verifies that `amount > 0`.
- **Balance Verification:** Queries current available balance using `SELECT balance, status FROM accounts WHERE account_number = ? FOR UPDATE`.
- **Insufficient Funds Prevention:** If `balance < amount`, rejects the withdrawal, displays a detailed shortfall breakdown (available vs requested), records a `FAILED` transaction audit log, and rolls back balance modifications.
- **Atomic Balance Update:** Decrements balance and commits transaction record atomically.
- **Confirmation / Error Display:** Displays formatted receipt on success or detailed error receipt on failure.

### Task 3: Check Account Balance (`BalanceInquiry.java`)
- **Live Database Retrieval (Strict Requirement):**
  - **No caching in local or static variables.**
  - Every call to `checkBalance()` or `getAccountDetailsFromDatabase()` executes a fresh SQL `SELECT` query:
    ```sql
    SELECT account_number, account_holder_name, account_type, balance, status, created_at 
    FROM accounts WHERE account_number = ?
    ```
- **Information Displayed:**
  - Account Number
  - Account Holder Name
  - Account Type (Savings, Checking)
  - Account Status (ACTIVE)
  - Date Account Opened
  - Current Available Balance (formatted as currency)

### Task 4: Money Transfer (`MoneyTransfer.java`)
- **Recipient Verification:** Verifies recipient account exists and is `ACTIVE`.
- **Sender Balance Check:** Confirms sender has sufficient available funds.
- **Same-Account Transfer Prevention:** Rejects transfers where sender and recipient accounts are identical.
- **Database Transaction Mechanism (ACID Atomicity):**
  - Enforces all-or-nothing execution:
    ```java
    conn.setAutoCommit(false); // Begin transaction
    try {
        // 1. Debit sender
        // 2. Credit recipient
        // 3. Insert sender transaction (TRANSFER_DEBIT)
        // 4. Insert recipient transaction (TRANSFER_CREDIT)
        conn.commit(); // Both succeed together
    } catch (SQLException e) {
        conn.rollback(); // Neither is committed on any failure
        throw e;
    } finally {
        conn.setAutoCommit(true);
    }
    ```
- **Dual Transaction Recording:** Records both `TRANSFER_DEBIT` (with counterparty reference) for sender and `TRANSFER_CREDIT` for recipient.
- **Confirmation Display:** Prints receipt showing transfer reference ID, parties involved, transfer amount, and sender's remaining balance.

### Task 5: Transaction History (`TransactionHistory.java`)
- **History Retrieval:** Queries `transactions` table filtered by `account_number` ordered by `date_time DESC`.
- **Fields Displayed:**
  - Transaction ID
  - Transaction Type (`DEPOSIT`, `WITHDRAWAL`, `TRANSFER_DEBIT`, `TRANSFER_CREDIT`)
  - Transaction Amount (formatted with `+` or `-` sign)
  - Date and Time (`yyyy-MM-dd HH:mm:ss`)
  - Status (`SUCCESS`, `FAILED`)
  - Description / Reference Account
- **Summary Statistics:** Displays total count, total credits, and total debits at the bottom of the tabular statement.

---

## Database Setup

1. Make sure MySQL is running on your machine:
   ```bash
   mysql -u root -p
   ```
2. Execute the schema script:
   ```sql
   SOURCE schema.sql;
   ```
   Or import via terminal:
   ```bash
   mysql -u root -p < schema.sql
   ```
3. Update `db.properties` with your database credentials if different:
   ```properties
   db.driver=com.mysql.cj.jdbc.Driver
   db.url=jdbc:mysql://localhost:3306/bank_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&createDatabaseIfNotExist=true
   db.user=root
   db.password=your_password
   ```

*Note: The system comes pre-seeded with 3 test accounts:*
- Account `1001`: Alice Smith (Balance: $5,000.00)
- Account `1002`: Bob Johnson (Balance: $2,500.00)
- Account `1003`: Charlie Davis (Balance: $1,200.00)

---

## Compilation & Execution

### 1. Compile All Files
```bash
javac *.java
```

### 2. Run Individual Deliverables
Each deliverable has its own `main` method and can be executed independently:

- **Task 1 – Deposit:**
  ```bash
  java -cp .:mysql-connector-j-8.x.x.jar Deposit
  ```
- **Task 2 – Withdraw:**
  ```bash
  java -cp .:mysql-connector-j-8.x.x.jar Withdraw
  ```
- **Task 3 – Check Balance:**
  ```bash
  java -cp .:mysql-connector-j-8.x.x.jar BalanceInquiry
  ```
- **Task 4 – Money Transfer:**
  ```bash
  java -cp .:mysql-connector-j-8.x.x.jar MoneyTransfer
  ```
- **Task 5 – Transaction History:**
  ```bash
  java -cp .:mysql-connector-j-8.x.x.jar TransactionHistory
  ```

### 3. Run the Integrated Interactive Menu
```bash
java -cp .:mysql-connector-j-8.x.x.jar BankAppMenu
```
