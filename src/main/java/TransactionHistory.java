import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

/**
 * Task 5: Transaction History
 * Member 3 – Banking Operations Developer
 *
 * Requirements:
 * - Display previous transactions.
 * - Show transaction ID.
 * - Show transaction type.
 * - Show transaction amount.
 * - Show date and time.
 * - Show transaction status.
 */
public class TransactionHistory {

    private static final NumberFormat CURRENCY_FORMAT = NumberFormat.getCurrencyInstance(Locale.US);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Model representing a single transaction record.
     */
    public static class TransactionRecord {
        private final String transactionId;
        private final String accountNumber;
        private final String transactionType;
        private final double amount;
        private final Timestamp dateTime;
        private final String status;
        private final String referenceAccount;
        private final String description;

        public TransactionRecord(String transactionId, String accountNumber, String transactionType,
                                 double amount, Timestamp dateTime, String status,
                                 String referenceAccount, String description) {
            this.transactionId = transactionId;
            this.accountNumber = accountNumber;
            this.transactionType = transactionType;
            this.amount = amount;
            this.dateTime = dateTime;
            this.status = status;
            this.referenceAccount = referenceAccount;
            this.description = description;
        }

        public String getTransactionId() { return transactionId; }
        public String getAccountNumber() { return accountNumber; }
        public String getTransactionType() { return transactionType; }
        public double getAmount() { return amount; }
        public Timestamp getDateTime() { return dateTime; }
        public String getStatus() { return status; }
        public String getReferenceAccount() { return referenceAccount; }
        public String getDescription() { return description; }
    }

    /**
     * Queries and displays all past transactions for a specified account.
     *
     * @param accountNumber The account whose statement/history is to be displayed
     * @return true if history was successfully fetched and displayed; false otherwise
     */
    public static boolean displayHistory(String accountNumber) {
        if (accountNumber == null || accountNumber.trim().isEmpty()) {
            System.err.println("\n[ERROR] Account number cannot be blank.");
            return false;
        }
        accountNumber = accountNumber.trim();

        Connection conn = null;
        PreparedStatement accStmt = null;
        PreparedStatement txnStmt = null;
        ResultSet accRs = null;
        ResultSet txnRs = null;

        try {
            conn = DatabaseConnection.getConnection();

            // 1. Verify account existence and retrieve holder name
            String accQuery = "SELECT account_holder_name, account_type, balance FROM accounts WHERE account_number = ?";
            accStmt = conn.prepareStatement(accQuery);
            accStmt.setString(1, accountNumber);
            accRs = accStmt.executeQuery();

            if (!accRs.next()) {
                System.err.println("\n[ERROR] Account number '" + accountNumber + "' does not exist.");
                return false;
            }

            String holderName = accRs.getString("account_holder_name");
            String accType = accRs.getString("account_type");
            double currentBalance = accRs.getDouble("balance");

            // 2. Retrieve transaction history ordered by latest first
            String txnQuery = "SELECT transaction_id, account_number, transaction_type, amount, date_time, status, reference_account, description "
                    + "FROM transactions WHERE account_number = ? ORDER BY date_time DESC";
            txnStmt = conn.prepareStatement(txnQuery);
            txnStmt.setString(1, accountNumber);
            txnRs = txnStmt.executeQuery();

            List<TransactionRecord> transactions = new ArrayList<>();
            while (txnRs.next()) {
                transactions.add(new TransactionRecord(
                        txnRs.getString("transaction_id"),
                        txnRs.getString("account_number"),
                        txnRs.getString("transaction_type"),
                        txnRs.getDouble("amount"),
                        txnRs.getTimestamp("date_time"),
                        txnRs.getString("status"),
                        txnRs.getString("reference_account"),
                        txnRs.getString("description")
                ));
            }

            // 3. Render Formatted Table
            renderTransactionTable(accountNumber, holderName, accType, currentBalance, transactions);
            return true;

        } catch (SQLException e) {
            System.err.println("\n[ERROR] Database error while retrieving transaction history: " + e.getMessage());
            return false;
        } finally {
            DatabaseConnection.close(accRs, txnRs, accStmt, txnStmt, conn);
        }
    }

    /**
     * Renders a tabular display of transaction records.
     */
    private static void renderTransactionTable(String accountNumber, String holderName, String accType,
                                               double currentBalance, List<TransactionRecord> list) {
        System.out.println("\n=======================================================================================================================");
        System.out.printf("                             OFFICIAL TRANSACTION STATEMENT / AUDIT HISTORY                                            %n");
        System.out.println("=======================================================================================================================");
        System.out.printf(" Account Number   : %-20s | Account Holder : %-30s%n", accountNumber, holderName);
        System.out.printf(" Account Type     : %-20s | Current Balance: %-30s%n", accType, CURRENCY_FORMAT.format(currentBalance));
        System.out.println("=======================================================================================================================");
        System.out.printf(" %-24s | %-16s | %-14s | %-19s | %-9s | %-24s%n",
                "TRANSACTION ID", "TYPE", "AMOUNT", "DATE & TIME", "STATUS", "DETAILS");
        System.out.println("-------------------------+------------------+----------------+---------------------+-----------+-----------------------");

        if (list.isEmpty()) {
            System.out.printf(" %-114s%n", "No transaction records found for this account.");
        } else {
            double totalDeposits = 0.0;
            double totalWithdrawals = 0.0;

            for (TransactionRecord t : list) {
                String type = t.getTransactionType();
                String sign = "+";
                if (type.equalsIgnoreCase("WITHDRAWAL") || type.equalsIgnoreCase("TRANSFER_DEBIT")) {
                    sign = "-";
                    if ("SUCCESS".equalsIgnoreCase(t.getStatus())) {
                        totalWithdrawals += t.getAmount();
                    }
                } else {
                    if ("SUCCESS".equalsIgnoreCase(t.getStatus())) {
                        totalDeposits += t.getAmount();
                    }
                }

                String formattedAmount = sign + CURRENCY_FORMAT.format(t.getAmount());
                String formattedDate = t.getDateTime() != null
                        ? t.getDateTime().toLocalDateTime().format(DATE_FORMAT)
                        : "N/A";
                String details = t.getDescription() != null ? t.getDescription() : "-";
                if (details.length() > 24) {
                    details = details.substring(0, 21) + "...";
                }

                System.out.printf(" %-24s | %-16s | %14s | %-19s | %-9s | %-24s%n",
                        t.getTransactionId(),
                        t.getTransactionType(),
                        formattedAmount,
                        formattedDate,
                        t.getStatus(),
                        details);
            }

            System.out.println("=======================================================================================================================");
            System.out.printf(" Total Records: %-5d | Total Credited: %-15s | Total Debited: %-15s%n",
                    list.size(), CURRENCY_FORMAT.format(totalDeposits), CURRENCY_FORMAT.format(totalWithdrawals));
        }
        System.out.println("=======================================================================================================================\n");
    }

    /**
     * Interactive console interface for Task 5: Transaction History.
     */
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("=======================================================");
        System.out.println("        BANKING OPERATIONS: TRANSACTION HISTORY        ");
        System.out.println("=======================================================");

        try {
            System.out.print("Enter Account Number: ");
            String accountNumber = scanner.nextLine().trim();

            displayHistory(accountNumber);

        } finally {
            // Keep scanner open if called from menu
        }
    }
}
