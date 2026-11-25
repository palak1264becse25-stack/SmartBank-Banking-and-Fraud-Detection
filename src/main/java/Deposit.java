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
 * Task 1: Deposit Money
 * Member 3 – Banking Operations Developer
 *
 * Requirements:
 * - Accept deposit amount.
 * - Validate that the amount is positive.
 * - Update the account balance.
 * - Record the transaction.
 * - Display transaction confirmation.
 */
public class Deposit {

    private static final NumberFormat CURRENCY_FORMAT = NumberFormat.getCurrencyInstance(Locale.US);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Executes the deposit operation within a safe database transaction.
     *
     * @param accountNumber The account to deposit funds into
     * @param amount The deposit amount (must be positive)
     * @return true if the deposit was successful; false otherwise
     */
    public static boolean deposit(String accountNumber, double amount) {
        // Step 1: Validate input
        if (accountNumber == null || accountNumber.trim().isEmpty()) {
            System.err.println("\n[ERROR] Account number cannot be blank.");
            return false;
        }
        accountNumber = accountNumber.trim();

        // Step 2: Validate that the amount is positive
        if (amount <= 0) {
            System.err.println("\n[ERROR] Invalid deposit amount: " + CURRENCY_FORMAT.format(amount));
            System.err.println("        Deposit amount must be strictly positive and greater than zero.");
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

            // Step 3: Verify account exists and retrieve current balance
            String checkSQL = "SELECT account_holder_name, balance, status FROM accounts WHERE account_number = ? FOR UPDATE";
            checkStmt = conn.prepareStatement(checkSQL);
            checkStmt.setString(1, accountNumber);
            rs = checkStmt.executeQuery();

            if (!rs.next()) {
                System.err.println("\n[ERROR] Deposit failed: Account number '" + accountNumber + "' does not exist.");
                conn.rollback();
                return false;
            }

            String accountHolder = rs.getString("account_holder_name");
            double currentBalance = rs.getDouble("balance");
            String status = rs.getString("status");

            if (!"ACTIVE".equalsIgnoreCase(status)) {
                System.err.println("\n[ERROR] Deposit failed: Account '" + accountNumber + "' is " + status + ".");
                conn.rollback();
                return false;
            }

            double newBalance = currentBalance + amount;

            // Step 4: Update the account balance in database
            String updateSQL = "UPDATE accounts SET balance = ? WHERE account_number = ?";
            updateStmt = conn.prepareStatement(updateSQL);
            updateStmt.setDouble(1, newBalance);
            updateStmt.setString(2, accountNumber);
            int rowsUpdated = updateStmt.executeUpdate();

            if (rowsUpdated == 0) {
                System.err.println("\n[ERROR] Deposit failed: Could not update account balance.");
                conn.rollback();
                return false;
            }

            // Step 5: Record the transaction in database
            String txnId = "DEP-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
            Timestamp now = Timestamp.valueOf(LocalDateTime.now());

            String txnSQL = "INSERT INTO transactions (transaction_id, account_number, transaction_type, amount, date_time, status, reference_account, description) "
                    + "VALUES (?, ?, 'DEPOSIT', ?, ?, 'SUCCESS', NULL, ?)";
            txnStmt = conn.prepareStatement(txnSQL);
            txnStmt.setString(1, txnId);
            txnStmt.setString(2, accountNumber);
            txnStmt.setDouble(3, amount);
            txnStmt.setTimestamp(4, now);
            txnStmt.setString(5, "Cash/Online Deposit to Account " + accountNumber);
            txnStmt.executeUpdate();

            // Commit transaction
            conn.commit();

            // Step 6: Display transaction confirmation
            displayConfirmation(txnId, accountNumber, accountHolder, currentBalance, amount, newBalance, now);
            return true;

        } catch (SQLException e) {
            System.err.println("\n[ERROR] Database error during deposit: " + e.getMessage());
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
     * Displays a formatted deposit confirmation receipt.
     */
    private static void displayConfirmation(String txnId, String accNumber, String holder,
                                            double oldBalance, double depositAmount,
                                            double newBalance, Timestamp timestamp) {
        System.out.println("\n=======================================================");
        System.out.println("             DEPOSIT TRANSACTION RECEIPT               ");
        System.out.println("=======================================================");
        System.out.printf(" Transaction ID   : %s%n", txnId);
        System.out.printf(" Transaction Type : DEPOSIT%n");
        System.out.printf(" Status           : SUCCESS%n");
        System.out.printf(" Date & Time      : %s%n", timestamp.toLocalDateTime().format(DATE_FORMAT));
        System.out.println("-------------------------------------------------------");
        System.out.printf(" Account Number   : %s%n", accNumber);
        System.out.printf(" Account Holder   : %s%n", holder);
        System.out.printf(" Previous Balance : %s%n", CURRENCY_FORMAT.format(oldBalance));
        System.out.printf(" Amount Deposited : %s%n", CURRENCY_FORMAT.format(depositAmount));
        System.out.println("-------------------------------------------------------");
        System.out.printf(" NEW BALANCE      : %s%n", CURRENCY_FORMAT.format(newBalance));
        System.out.println("=======================================================");
        System.out.println(" [Funds successfully deposited and confirmed in DB]");
        System.out.println("=======================================================\n");
    }

    /**
     * Interactive console interface for Task 1: Deposit Money.
     */
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("=======================================================");
        System.out.println("            BANKING OPERATIONS: DEPOSIT MONEY          ");
        System.out.println("=======================================================");

        try {
            System.out.print("Enter Account Number: ");
            String accountNumber = scanner.nextLine().trim();

            System.out.print("Enter Deposit Amount ($): ");
            String amountInput = scanner.nextLine().trim();

            double amount;
            try {
                amount = Double.parseDouble(amountInput);
            } catch (NumberFormatException e) {
                System.err.println("\n[ERROR] Invalid number format. Please enter a valid numerical amount.");
                return;
            }

            deposit(accountNumber, amount);

        } finally {
            // Keep scanner open if invoked from menu, or close if standalone
        }
    }
}
