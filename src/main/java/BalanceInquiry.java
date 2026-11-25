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

/**
 * Task 3: Check Account Balance
 * Member 3 – Banking Operations Developer
 *
 * Requirements:
 * - Retrieve the account balance.
 * - Display the current balance.
 * - Display account information.
 * - Ensure the balance is retrieved from the database rather than being permanently stored in a local variable.
 */
public class BalanceInquiry {

    private static final NumberFormat CURRENCY_FORMAT = NumberFormat.getCurrencyInstance(Locale.US);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * DTO to encapsulate live account information fetched from the database.
     * Note: Instances are created transiently per query and never persisted
     * as permanent state, honoring the requirement that balances are always live.
     */
    public static class AccountDetails {
        private final String accountNumber;
        private final String accountHolderName;
        private final String accountType;
        private final double balance;
        private final String status;
        private final Timestamp createdAt;

        public AccountDetails(String accountNumber, String accountHolderName, String accountType,
                              double balance, String status, Timestamp createdAt) {
            this.accountNumber = accountNumber;
            this.accountHolderName = accountHolderName;
            this.accountType = accountType;
            this.balance = balance;
            this.status = status;
            this.createdAt = createdAt;
        }

        public String getAccountNumber() { return accountNumber; }
        public String getAccountHolderName() { return accountHolderName; }
        public String getAccountType() { return accountType; }
        public double getBalance() { return balance; }
        public String getStatus() { return status; }
        public Timestamp getCreatedAt() { return createdAt; }
    }

    /**
     * Retrieves account balance and information DIRECTLY from the database.
     *
     * IMPORTANT ARCHITECTURAL REQUIREMENT:
     * This method executes a fresh SQL query against the 'accounts' table on EVERY invocation.
     * No local or static variables permanently hold or cache balance state.
     *
     * @param accountNumber The account number to query
     * @return AccountDetails object if found, or null if account does not exist / error occurs
     */
    public static AccountDetails getAccountDetailsFromDatabase(String accountNumber) {
        if (accountNumber == null || accountNumber.trim().isEmpty()) {
            System.err.println("\n[ERROR] Account number cannot be blank.");
            return null;
        }
        accountNumber = accountNumber.trim();

        String sql = "SELECT account_number, account_holder_name, account_type, balance, status, created_at "
                   + "FROM accounts WHERE account_number = ?";

        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            conn = DatabaseConnection.getConnection();
            stmt = conn.prepareStatement(sql);
            stmt.setString(1, accountNumber);
            rs = stmt.executeQuery();

            if (rs.next()) {
                String accNum = rs.getString("account_number");
                String holder = rs.getString("account_holder_name");
                String type = rs.getString("account_type");
                double liveBalance = rs.getDouble("balance"); // Fresh read from database
                String status = rs.getString("status");
                Timestamp createdAt = rs.getTimestamp("created_at");

                return new AccountDetails(accNum, holder, type, liveBalance, status, createdAt);
            } else {
                System.err.println("\n[ERROR] Account number '" + accountNumber + "' not found in database.");
                return null;
            }

        } catch (SQLException e) {
            System.err.println("\n[ERROR] Database error during balance inquiry: " + e.getMessage());
            return null;
        } finally {
            DatabaseConnection.close(rs, stmt, conn);
        }
    }

    /**
     * Retrieves and displays live balance and account information.
     *
     * @param accountNumber The account to check
     * @return true if inquiry succeeded; false otherwise
     */
    public static boolean checkBalance(String accountNumber) {
        AccountDetails details = getAccountDetailsFromDatabase(accountNumber);
        if (details == null) {
            return false;
        }

        displayAccountInformation(details);
        return true;
    }

    /**
     * Displays formatted account information and current balance.
     */
    private static void displayAccountInformation(AccountDetails details) {
        String inquiryTime = LocalDateTime.now().format(DATE_FORMAT);
        String createdTime = details.getCreatedAt() != null 
                ? details.getCreatedAt().toLocalDateTime().format(DATE_FORMAT) 
                : "N/A";

        System.out.println("\n=======================================================");
        System.out.println("              ACCOUNT BALANCE INQUIRY                  ");
        System.out.println("=======================================================");
        System.out.printf(" Inquiry Date/Time: %s%n", inquiryTime);
        System.out.printf(" Account Number   : %s%n", details.getAccountNumber());
        System.out.printf(" Account Holder   : %s%n", details.getAccountHolderName());
        System.out.printf(" Account Type     : %s%n", details.getAccountType());
        System.out.printf(" Account Status   : %s%n", details.getStatus());
        System.out.printf(" Account Opened   : %s%n", createdTime);
        System.out.println("-------------------------------------------------------");
        System.out.printf(" CURRENT BALANCE  : %s%n", CURRENCY_FORMAT.format(details.getBalance()));
        System.out.println("-------------------------------------------------------");
        System.out.println(" [Source: Live query retrieved directly from database]");
        System.out.println("=======================================================\n");
    }

    /**
     * Interactive console interface for Task 3: Check Account Balance.
     */
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("=======================================================");
        System.out.println("        BANKING OPERATIONS: CHECK ACCOUNT BALANCE      ");
        System.out.println("=======================================================");

        try {
            System.out.print("Enter Account Number: ");
            String accountNumber = scanner.nextLine().trim();

            checkBalance(accountNumber);

        } finally {
            // Keep scanner open if called from menu
        }
    }
}
