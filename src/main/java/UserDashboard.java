import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Scanner;
import com.smartbank.fraud.FraudAlertDashboard;
import com.smartbank.fraud.FraudDetection;
import com.smartbank.fraud.Transaction;

/** Unified customer workspace: authenticated user + MySQL banking operations. */
public class UserDashboard {
    private final UserAuthentication auth;
    private final Scanner scanner;
    private final FraudDetection fraudDetection = new FraudDetection();

    public UserDashboard(UserAuthentication auth, Scanner scanner) {
        this.auth = auth;
        this.scanner = scanner;
    }

    public void show() {
        while (auth.isLoggedIn()) {
            try {
                User user = auth.requireLogin();
                BalanceInquiry.AccountDetails live = BalanceInquiry.getAccountDetailsFromDatabase(user.getAccountNumber());
                if (live == null) {
                    System.out.println("Unable to load your bank account. Check MySQL and account setup.");
                    auth.logout();
                    return;
                }
                user.setBalance(live.getBalance());
                printHeader(user);
                System.out.print("Choose an option (1-8): ");
                String choice = scanner.nextLine().trim();
                switch (choice) {
                    case "1": viewAccountDetails(user); break;
                    case "2": deposit(user); break;
                    case "3": withdraw(user); break;
                    case "4": transfer(user); break;
                    case "5": TransactionHistory.displayHistory(user.getAccountNumber()); break;
                    case "6": runFraudDemo(); break;
                    case "7": BalanceInquiry.checkBalance(user.getAccountNumber()); break;
                    case "8": auth.logout(); System.out.println("Logged out successfully."); break;
                    default: System.out.println("Invalid option. Choose 1-8.");
                }
            } catch (AuthException e) {
                System.out.println("Access error: " + e.getMessage());
                auth.logout();
            } catch (java.util.NoSuchElementException e) {
                auth.logout();
                return;
            }
        }
    }

    private void printHeader(User user) {
        System.out.println("\n====================================================");
        System.out.println("             SMARTBANK CUSTOMER DASHBOARD           ");
        System.out.println("====================================================");
        System.out.println("Welcome, " + user.getFullName());
        System.out.println("Account: " + UserAuthentication.maskAccountNumber(user.getAccountNumber()));
        System.out.printf("Live balance: Rs. %.2f%n", user.getBalance());
        System.out.println("----------------------------------------------------");
        System.out.println("1. Account details       5. Transaction history");
        System.out.println("2. Deposit money         6. Fraud detection demo");
        System.out.println("3. Withdraw money        7. Refresh balance");
        System.out.println("4. Transfer funds        8. Logout");
        System.out.println("----------------------------------------------------");
    }

    private void viewAccountDetails(User u) {
        System.out.println("\n--- ACCOUNT DETAILS ---");
        System.out.println("Name: " + u.getFullName());
        System.out.println("Email: " + u.getEmail());
        System.out.println("Phone: " + u.getPhone());
        System.out.println("Username: " + u.getUsername());
        System.out.println("Account number: " + UserAuthentication.maskAccountNumber(u.getAccountNumber()));
        BalanceInquiry.checkBalance(u.getAccountNumber());
    }

    private void deposit(User u) {
        double amount = readAmount("Deposit amount (Rs.): ");
        if (amount > 0 && Deposit.deposit(u.getAccountNumber(), amount)) {
            fraudDetection.analyze(Transaction.deposit("APP-DEP-" + System.nanoTime(), u.getAccountNumber(), amount, LocalDateTime.now()))
                    .ifPresent(a -> System.out.println("FRAUD ALERT: " + a.getReasonSummary()));
        }
    }

    private void withdraw(User u) {
        double amount = readAmount("Withdrawal amount (Rs.): ");
        if (amount > 0 && Withdraw.withdraw(u.getAccountNumber(), amount)) {
            fraudDetection.analyze(Transaction.withdrawal("APP-WDR-" + System.nanoTime(), u.getAccountNumber(), amount, LocalDateTime.now()))
                    .ifPresent(a -> System.out.println("FRAUD ALERT: " + a.getReasonSummary()));
        }
    }

    private void transfer(User u) {
        System.out.print("Recipient account number: ");
        String recipient = scanner.nextLine().trim();
        double amount = readAmount("Transfer amount (Rs.): ");
        if (amount > 0 && MoneyTransfer.transfer(u.getAccountNumber(), recipient, amount)) {
            fraudDetection.analyze(Transaction.transfer("APP-TRF-" + System.nanoTime(), u.getAccountNumber(), recipient,
                    amount, LocalDateTime.now())).ifPresent(a -> System.out.println("FRAUD ALERT: " + a.getReasonSummary()));
        }
    }

    private double readAmount(String prompt) {
        System.out.print(prompt);
        try {
            double amount = Double.parseDouble(scanner.nextLine().trim());
            if (!Double.isFinite(amount) || amount <= 0) throw new NumberFormatException();
            return amount;
        } catch (NumberFormatException e) {
            System.out.println("Enter a valid positive amount.");
            return -1;
        }
    }

    private void runFraudDemo() {
        System.out.println("\nFraud detection runs on transactions performed during this login session.");
        System.out.println("The separate review dashboard below loads its own demonstration dataset.");
        FraudDetection demo = new FraudDetection();
        FraudAlertDashboard dashboard = new FraudAlertDashboard(demo, scanner);
        dashboard.start("Fraud alert review workspace");
    }
}
