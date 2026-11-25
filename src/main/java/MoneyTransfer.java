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
 * Task 4: Money Transfer
 * Member 3 – Banking Operations Developer
 *
 * Requirements:
 * - Accept the recipient's account number.
 * - Accept the transfer amount.
 * - Verify that the recipient account exists.
 * - Check the sender's available balance.
 * - Record the transfer.
 * - Display transaction confirmation.
 * - For transfers, both debit and credit operations must succeed together or neither
 *   should be committed. You will provide the database transaction mechanism for this.
 */
public class MoneyTransfer {

    private static final NumberFormat CURRENCY_FORMAT = NumberFormat.getCurrencyInstance(Locale.US);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Executes an atomic funds transfer between sender and recipient accounts.
     *
     * Implements ACID transactional semantics:
     * 1. conn.setAutoCommit(false) disables automatic commits.
     * 2. Sender debit, recipient credit, and dual transaction records execute.
     * 3. conn.commit() is invoked only when all operations succeed.
     * 4. Any failure triggers conn.rollback() to leave database state untouched.
     *
     * @param senderAccount Account sending funds
     * @param recipientAccount Account receiving funds
     * @param amount Monetary amount to transfer
     * @return true if transfer completed and committed successfully; false otherwise
     */
    public static boolean transfer(String senderAccount, String recipientAccount, double amount) {
        // Step 1: Input Validation
        if (senderAccount == null || senderAccount.trim().isEmpty()) {
            System.err.println("\n[ERROR] Sender account number cannot be blank.");
            return false;
        }
        if (recipientAccount == null || recipientAccount.trim().isEmpty()) {
            System.err.println("\n[ERROR] Recipient account number cannot be blank.");
            return false;
        }
        senderAccount = senderAccount.trim();
        recipientAccount = recipientAccount.trim();

        if (senderAccount.equalsIgnoreCase(recipientAccount)) {
            System.err.println("\n[ERROR] Invalid transfer: Sender and recipient account numbers cannot be identical.");
            return false;
        }

        if (amount <= 0) {
            System.err.println("\n[ERROR] Invalid transfer amount: " + CURRENCY_FORMAT.format(amount));
            System.err.println("        Transfer amount must be strictly positive and greater than zero.");
            return false;
        }

        Connection conn = null;
        PreparedStatement senderStmt = null;
        PreparedStatement recipientStmt = null;
        PreparedStatement debitStmt = null;
        PreparedStatement creditStmt = null;
        PreparedStatement txnDebitStmt = null;
        PreparedStatement txnCreditStmt = null;
        ResultSet senderRs = null;
        ResultSet recipientRs = null;

        try {
            conn = DatabaseConnection.getConnection();

            // =================================================================
            // DATABASE TRANSACTION MECHANISM (ACID ATOMICITY)
            // =================================================================
            conn.setAutoCommit(false); // 1. Start atomic transaction

            // Step 2: Verify Sender Account & Check Available Balance
            String senderQuery = "SELECT account_holder_name, balance, status FROM accounts WHERE account_number = ? FOR UPDATE";
            senderStmt = conn.prepareStatement(senderQuery);
            senderStmt.setString(1, senderAccount);
            senderRs = senderStmt.executeQuery();

            if (!senderRs.next()) {
                System.err.println("\n[ERROR] Transfer failed: Sender account '" + senderAccount + "' does not exist.");
                conn.rollback();
                return false;
            }

            String senderName = senderRs.getString("account_holder_name");
            double senderBalance = senderRs.getDouble("balance");
            String senderStatus = senderRs.getString("status");

            if (!"ACTIVE".equalsIgnoreCase(senderStatus)) {
                System.err.println("\n[ERROR] Transfer failed: Sender account '" + senderAccount + "' is " + senderStatus + ".");
                conn.rollback();
                return false;
            }

            if (senderBalance < amount) {
                System.err.println("\n=======================================================");
                System.err.println("            TRANSFER REJECTED: INSUFFICIENT FUNDS      ");
                System.err.println("=======================================================");
                System.err.printf(" Sender Account   : %s (%s)%n", senderAccount, senderName);
                System.err.printf(" Available Balance: %s%n", CURRENCY_FORMAT.format(senderBalance));
                System.err.printf(" Transfer Amount  : %s%n", CURRENCY_FORMAT.format(amount));
                System.err.printf(" Shortfall        : %s%n", CURRENCY_FORMAT.format(amount - senderBalance));
                System.err.println("-------------------------------------------------------");
                System.err.println(" [Action Prevented: Transaction aborted with zero changes]");
                System.err.println("=======================================================\n");
                conn.rollback();
                return false;
            }

            // Step 3: Verify Recipient Account Exists
            String recipientQuery = "SELECT account_holder_name, status FROM accounts WHERE account_number = ? FOR UPDATE";
            recipientStmt = conn.prepareStatement(recipientQuery);
            recipientStmt.setString(1, recipientAccount);
            recipientRs = recipientStmt.executeQuery();

            if (!recipientRs.next()) {
                System.err.println("\n[ERROR] Transfer failed: Recipient account '" + recipientAccount + "' does not exist.");
                conn.rollback();
                return false;
            }

            String recipientName = recipientRs.getString("account_holder_name");
            String recipientStatus = recipientRs.getString("status");

            if (!"ACTIVE".equalsIgnoreCase(recipientStatus)) {
                System.err.println("\n[ERROR] Transfer failed: Recipient account '" + recipientAccount + "' is " + recipientStatus + ".");
                conn.rollback();
                return false;
            }

            double senderNewBalance = senderBalance - amount;

            // Step 4: Debit the Sender's Account
            String debitSQL = "UPDATE accounts SET balance = balance - ? WHERE account_number = ?";
            debitStmt = conn.prepareStatement(debitSQL);
            debitStmt.setDouble(1, amount);
            debitStmt.setString(2, senderAccount);
            int debitRows = debitStmt.executeUpdate();

            if (debitRows == 0) {
                System.err.println("\n[ERROR] Transfer failed: Could not debit sender account.");
                conn.rollback();
                return false;
            }

            // Step 5: Credit the Recipient's Account
            String creditSQL = "UPDATE accounts SET balance = balance + ? WHERE account_number = ?";
            creditStmt = conn.prepareStatement(creditSQL);
            creditStmt.setDouble(1, amount);
            creditStmt.setString(2, recipientAccount);
            int creditRows = creditStmt.executeUpdate();

            if (creditRows == 0) {
                System.err.println("\n[ERROR] Transfer failed: Could not credit recipient account.");
                conn.rollback();
                return false;
            }

            // Step 6: Record the Transfer in Transactions Table (Both Sides)
            String commonTxnId = "TRF-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
            Timestamp now = Timestamp.valueOf(LocalDateTime.now());

            // 6a. Sender Debit Record
            String txnDebitSQL = "INSERT INTO transactions (transaction_id, account_number, transaction_type, amount, date_time, status, reference_account, description) "
                    + "VALUES (?, ?, 'TRANSFER_DEBIT', ?, ?, 'SUCCESS', ?, ?)";
            txnDebitStmt = conn.prepareStatement(txnDebitSQL);
            txnDebitStmt.setString(1, commonTxnId + "-OUT");
            txnDebitStmt.setString(2, senderAccount);
            txnDebitStmt.setDouble(3, amount);
            txnDebitStmt.setTimestamp(4, now);
            txnDebitStmt.setString(5, recipientAccount);
            txnDebitStmt.setString(6, "Transfer sent to Acc: " + recipientAccount + " (" + recipientName + ")");
            txnDebitStmt.executeUpdate();

            // 6b. Recipient Credit Record
            String txnCreditSQL = "INSERT INTO transactions (transaction_id, account_number, transaction_type, amount, date_time, status, reference_account, description) "
                    + "VALUES (?, ?, 'TRANSFER_CREDIT', ?, ?, 'SUCCESS', ?, ?)";
            txnCreditStmt = conn.prepareStatement(txnCreditSQL);
            txnCreditStmt.setString(1, commonTxnId + "-IN");
            txnCreditStmt.setString(2, recipientAccount);
            txnCreditStmt.setDouble(3, amount);
            txnCreditStmt.setTimestamp(4, now);
            txnCreditStmt.setString(5, senderAccount);
            txnCreditStmt.setString(6, "Transfer received from Acc: " + senderAccount + " (" + senderName + ")");
            txnCreditStmt.executeUpdate();

            // Step 7: Commit Database Transaction (Atomicity Guarantee)
            conn.commit();

            // Step 8: Display Confirmation Receipt
            displayConfirmation(commonTxnId, senderAccount, senderName, recipientAccount, recipientName,
                    amount, senderNewBalance, now);
            return true;

        } catch (SQLException e) {
            System.err.println("\n[ERROR] Database error during money transfer: " + e.getMessage());
            if (conn != null) {
                try {
                    conn.rollback();
                    System.err.println("[INFO] ACID Transaction rolled back: Neither debit nor credit was committed.");
                } catch (SQLException rollbackEx) {
                    System.err.println("[ERROR] Failed to rollback transaction: " + rollbackEx.getMessage());
                }
            }
            return false;
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true); // Restore default
                } catch (SQLException ignored) {
                }
            }
            DatabaseConnection.close(senderRs, recipientRs, senderStmt, recipientStmt,
                    debitStmt, creditStmt, txnDebitStmt, txnCreditStmt, conn);
        }
    }

    /**
     * Displays a formatted money transfer confirmation receipt.
     */
    private static void displayConfirmation(String txnRef, String senderAcc, String senderName,
                                            String recipientAcc, String recipientName,
                                            double transferAmount, double senderRemainingBalance,
                                            Timestamp timestamp) {
        System.out.println("\n=======================================================");
        System.out.println("             MONEY TRANSFER CONFIRMATION RECEIPT       ");
        System.out.println("=======================================================");
        System.out.printf(" Transfer Reference: %s%n", txnRef);
        System.out.printf(" Status            : SUCCESS (ACID Committed)%n");
        System.out.printf(" Date & Time       : %s%n", timestamp.toLocalDateTime().format(DATE_FORMAT));
        System.out.println("-------------------------------------------------------");
        System.out.printf(" SENDER ACCOUNT    : %s%n", senderAcc);
        System.out.printf(" Sender Name       : %s%n", senderName);
        System.out.printf(" RECIPIENT ACCOUNT : %s%n", recipientAcc);
        System.out.printf(" Recipient Name    : %s%n", recipientName);
        System.out.println("-------------------------------------------------------");
        System.out.printf(" Transfer Amount   : %s%n", CURRENCY_FORMAT.format(transferAmount));
        System.out.printf(" Sender New Balance: %s%n", CURRENCY_FORMAT.format(senderRemainingBalance));
        System.out.println("=======================================================");
        System.out.println(" [Guaranteed: Debit and Credit executed simultaneously]");
        System.out.println("=======================================================\n");
    }

    /**
     * Interactive console interface for Task 4: Money Transfer.
     */
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("=======================================================");
        System.out.println("           BANKING OPERATIONS: MONEY TRANSFER          ");
        System.out.println("=======================================================");

        try {
            System.out.print("Enter Sender Account Number: ");
            String senderAccount = scanner.nextLine().trim();

            System.out.print("Enter Recipient Account Number: ");
            String recipientAccount = scanner.nextLine().trim();

            System.out.print("Enter Transfer Amount ($): ");
            String amountInput = scanner.nextLine().trim();

            double amount;
            try {
                amount = Double.parseDouble(amountInput);
            } catch (NumberFormatException e) {
                System.err.println("\n[ERROR] Invalid number format. Please enter a valid numerical amount.");
                return;
            }

            transfer(senderAccount, recipientAccount, amount);

        } finally {
            // Keep scanner open if called from menu
        }
    }
}
