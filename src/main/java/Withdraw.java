import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Scanner;
import java.util.UUID;

/**
 * Task 2: Withdraw Money
 * Member 3 – Banking Operations Developer
 *
 * Requirements:
 * - Accept withdrawal amount.
 * - Check available balance.
 * - Prevent withdrawal if the balance is insufficient.
 * - Update the account balance.
 * - Record the transaction.
 * - Display confirmation or an appropriate error.
 */
public class Withdraw {

    private static final NumberFormat CURRENCY_FORMAT = NumberFormat.getCurrencyInstance(Locale.US);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Executes the withdrawal operation within a safe database transaction.
     *
     * @param accountNumber The account to withdraw funds from
     * @param amount The withdrawal amount (must be positive and <= available balance)
     * @return true if the withdrawal was successful; false otherwise
     */
    public static boolean withdraw(String accountNumber, double amount) {
        // Step 1: Validate input
        if (accountNumber == null || accountNumber.trim().isEmpty()) {
            System.err.println("\n[ERROR] Account number cannot be blank.");
            return false;
        }
        accountNumber = accountNumber.trim();

        // Step 2: Validate that the amount is positive
        if (amount <= 0) {
            System.err.println("\n[ERROR] Invalid withdrawal amount: " + CURRENCY_FORMAT.format(amount));
            System.err.println("        Withdrawal amount must be strictly positive and greater than zero.");
            return false;
        }

        Connection conn = null;
        PreparedStatement checkStmt = null;
        PreparedStatement updateStmt = null;
        PreparedStatement txnStmt = null;
        ResultSet rs = null;

        try {
            conn = DatabaseConnection.getConnection();

            // Begin atomic database transaction
            conn.setAutoCommit(false);

            // Step 3: Check available balance and account status from the database
            String checkSQL = "SELECT account_holder_name, balance, status FROM accounts WHERE account_number = ? FOR UPDATE";
            checkStmt = conn.prepareStatement(checkSQL);
            checkStmt.setString(1, accountNumber);
            rs = checkStmt.executeQuery();

            if (!rs.next()) {
                System.err.println("\n[ERROR] Withdrawal failed: Account number '" + accountNumber + "' does not exist.");
                conn.rollback();
                return false;
            }

            String accountHolder = rs.getString("account_holder_name");
            double currentBalance = rs.getDouble("balance");
            String status = rs.getString("status");

            if (!"ACTIVE".equalsIgnoreCase(status)) {
                System.err.println("\n[ERROR] Withdrawal failed: Account '" + accountNumber + "' is " + status + ".");
                conn.rollback();
                return false;
            }

            // Step 4: Prevent withdrawal if the balance is insufficient
            if (currentBalance < amount) {
                displayInsufficientBalanceError(accountNumber, accountHolder, currentBalance, amount);
                recordFailedWithdrawal(conn, accountNumber, amount, "Insufficient balance");
                conn.commit(); // Commit the audit log of failed attempt
                return false;
            }

            double newBalance = currentBalance - amount;

            // Step 5: Update the account balance in database
            String updateSQL = "UPDATE accounts SET balance = ? WHERE account_number = ?";
            updateStmt = conn.prepareStatement(updateSQL);
            updateStmt.setDouble(1, newBalance);
            updateStmt.setString(2, accountNumber);
            int rowsUpdated = updateStmt.executeUpdate();

            if (rowsUpdated == 0) {
                System.err.println("\n[ERROR] Withdrawal failed: Could not update account balance.");
                conn.rollback();
                return false;
            }

            // Step 6: Record the successful transaction in database
            String txnId = "WTH-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
            Timestamp now = Timestamp.valueOf(LocalDateTime.now());

            String txnSQL = "INSERT INTO transactions (transaction_id, account_number, transaction_type, amount, date_time, status, reference_account, description) "
                    + "VALUES (?, ?, 'WITHDRAWAL', ?, ?, 'SUCCESS', NULL, ?)";
            txnStmt = conn.prepareStatement(txnSQL);
            txnStmt.setString(1, txnId);
            txnStmt.setString(2, accountNumber);
            txnStmt.setDouble(3, amount);
            txnStmt.setTimestamp(4, now);
            txnStmt.setString(5, "Cash/ATM Withdrawal from Account " + accountNumber);
            txnStmt.executeUpdate();

            // Commit atomic transaction
            conn.commit();

            // Step 7: Display transaction confirmation
            displayConfirmation(txnId, accountNumber, accountHolder, currentBalance, amount, newBalance, now);
            return true;

        } catch (SQLException e) {
            System.err.println("\n[ERROR] Database error during withdrawal: " + e.getMessage());
            if (conn != null) {
                try {
                    conn.rollback();
                    System.err.println("[INFO] Transaction rolled back successfully.");
                } catch (SQLException rollbackEx) {
                    System.err.println("[ERROR] Failed to rollback transaction: " + rollbackEx.getMessage());
                }
            }
            return false;
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                } catch (SQLException ignored) {
                }
            }
            DatabaseConnection.close(rs, checkStmt, updateStmt, txnStmt, conn);
        }
    }

    /**
     * Records a failed withdrawal attempt in the transactions table for security/audit purposes.
     */
    private static void recordFailedWithdrawal(Connection conn, String accountNumber, double amount, String reason) {
        String failedTxnSQL = "INSERT INTO transactions (transaction_id, account_number, transaction_type, amount, date_time, status, reference_account, description) "
                + "VALUES (?, ?, 'WITHDRAWAL', ?, ?, 'FAILED', NULL, ?)";
        String txnId = "FAIL-WTH-" + System.currentTimeMillis();
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        try (PreparedStatement stmt = conn.prepareStatement(failedTxnSQL)) {
            stmt.setString(1, txnId);
            stmt.setString(2, accountNumber);
            stmt.setDouble(3, amount);
            stmt.setTimestamp(4, now);
            stmt.setString(5, "Declined: " + reason);
            stmt.executeUpdate();
        } catch (SQLException ignored) {
            // Non-critical audit logging failure
        }
    }

    /**
     * Displays a formatted insufficient balance error message.
     */
    private static void displayInsufficientBalanceError(String accNumber, String holder, double available, double requested) {
        double shortfall = requested - available;
        System.err.println("\n=======================================================");
        System.err.println("            TRANSACTION REJECTED: INSUFFICIENT FUNDS   ");
        System.err.println("=======================================================");
        System.err.printf(" Account Number   : %s%n", accNumber);
        System.err.printf(" Account Holder   : %s%n", holder);
        System.err.printf(" Available Balance: %s%n", CURRENCY_FORMAT.format(available));
        System.err.printf(" Requested Amount : %s%n", CURRENCY_FORMAT.format(requested));
        System.err.printf(" Shortfall Amount : %s%n", CURRENCY_FORMAT.format(shortfall));
        System.err.println("-------------------------------------------------------");
        System.err.println(" [Action Prevented: Account does not have sufficient balance]");
        System.err.println("=======================================================\n");
    }

    /**
     * Displays a formatted withdrawal confirmation receipt.
     */
    private static void displayConfirmation(String txnId, String accNumber, String holder,
                                            double oldBalance, double withdrawAmount,
                                            double newBalance, Timestamp timestamp) {
        System.out.println("\n=======================================================");
        System.out.println("            WITHDRAWAL TRANSACTION RECEIPT             ");
        System.out.println("=======================================================");
        System.out.printf(" Transaction ID   : %s%n", txnId);
        System.out.printf(" Transaction Type : WITHDRAWAL%n");
        System.out.printf(" Status           : SUCCESS%n");
        System.out.printf(" Date & Time      : %s%n", timestamp.toLocalDateTime().format(DATE_FORMAT));
        System.out.println("-------------------------------------------------------");
        System.out.printf(" Account Number   : %s%n", accNumber);
        System.out.printf(" Account Holder   : %s%n", holder);
        System.out.printf(" Previous Balance : %s%n", CURRENCY_FORMAT.format(oldBalance));
        System.out.printf(" Amount Withdrawn : %s%n", CURRENCY_FORMAT.format(withdrawAmount));
        System.out.println("-------------------------------------------------------");
        System.out.printf(" REMAINING BALANCE: %s%n", CURRENCY_FORMAT.format(newBalance));
        System.out.println("=======================================================");
        System.out.println(" [Funds successfully withdrawn and confirmed in DB]");
        System.out.println("=======================================================\n");
    }

    /**
     * Interactive console interface for Task 2: Withdraw Money.
     */
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("=======================================================");
        System.out.println("           BANKING OPERATIONS: WITHDRAW MONEY          ");
        System.out.println("=======================================================");

        try {
            System.out.print("Enter Account Number: ");
            String accountNumber = scanner.nextLine().trim();

            System.out.print("Enter Withdrawal Amount ($): ");
            String amountInput = scanner.nextLine().trim();

            double amount;
            try {
                amount = Double.parseDouble(amountInput);
            } catch (NumberFormatException e) {
                System.err.println("\n[ERROR] Invalid number format. Please enter a valid numerical amount.");
                return;
            }

            withdraw(accountNumber, amount);

        } finally {
            // Keep scanner open if invoked from menu
        }
    }
}
