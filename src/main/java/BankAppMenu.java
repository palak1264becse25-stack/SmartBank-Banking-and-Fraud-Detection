import java.util.Scanner;

/**
 * BankAppMenu
 * 
 * Integrated Interactive Console Dashboard for Member 3 – Banking Operations.
 * Allows quick navigation and testing of all 5 deliverables:
 * 1. Deposit.java
 * 2. Withdraw.java
 * 3. BalanceInquiry.java
 * 4. MoneyTransfer.java
 * 5. TransactionHistory.java
 */
public class BankAppMenu {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        boolean running = true;

        while (running) {
            System.out.println("\n=======================================================");
            System.out.println("       CORE BANKING SYSTEM - MEMBER 3 OPERATIONS       ");
            System.out.println("=======================================================");
            System.out.println(" 1. Deposit Money");
            System.out.println(" 2. Withdraw Money");
            System.out.println(" 3. Check Account Balance (Live Database Inquiry)");
            System.out.println(" 4. Money Transfer (ACID Atomic Debit/Credit)");
            System.out.println(" 5. Transaction History & Audit Statement");
            System.out.println(" 6. Test Database Connection");
            System.out.println(" 7. Exit");
            System.out.println("=======================================================");
            System.out.print("Select an option (1-7): ");

            String choice = scanner.nextLine().trim();

            switch (choice) {
                case "1":
                    handleDeposit(scanner);
                    break;
                case "2":
                    handleWithdraw(scanner);
                    break;
                case "3":
                    handleBalanceInquiry(scanner);
                    break;
                case "4":
                    handleMoneyTransfer(scanner);
                    break;
                case "5":
                    handleTransactionHistory(scanner);
                    break;
                case "6":
                    DatabaseConnection.main(new String[]{});
                    break;
                case "7":
                    System.out.println("\nThank you for using the Banking Operations System. Goodbye!");
                    running = false;
                    break;
                default:
                    System.err.println("\n[ERROR] Invalid selection. Please enter a number between 1 and 7.");
                    break;
            }
        }
        scanner.close();
    }

    private static void handleDeposit(Scanner scanner) {
        System.out.println("\n--- [TASK 1: DEPOSIT MONEY] ---");
        System.out.print("Enter Account Number: ");
        String acc = scanner.nextLine().trim();
        System.out.print("Enter Deposit Amount ($): ");
        String amtStr = scanner.nextLine().trim();

        try {
            double amt = Double.parseDouble(amtStr);
            Deposit.deposit(acc, amt);
        } catch (NumberFormatException e) {
            System.err.println("\n[ERROR] Invalid amount entered. Must be a numeric value.");
        }
    }

    private static void handleWithdraw(Scanner scanner) {
        System.out.println("\n--- [TASK 2: WITHDRAW MONEY] ---");
        System.out.print("Enter Account Number: ");
        String acc = scanner.nextLine().trim();
        System.out.print("Enter Withdrawal Amount ($): ");
        String amtStr = scanner.nextLine().trim();

        try {
            double amt = Double.parseDouble(amtStr);
            Withdraw.withdraw(acc, amt);
        } catch (NumberFormatException e) {
            System.err.println("\n[ERROR] Invalid amount entered. Must be a numeric value.");
        }
    }

    private static void handleBalanceInquiry(Scanner scanner) {
        System.out.println("\n--- [TASK 3: CHECK ACCOUNT BALANCE] ---");
        System.out.print("Enter Account Number: ");
        String acc = scanner.nextLine().trim();
        BalanceInquiry.checkBalance(acc);
    }

    private static void handleMoneyTransfer(Scanner scanner) {
        System.out.println("\n--- [TASK 4: MONEY TRANSFER] ---");
        System.out.print("Enter Sender Account Number: ");
        String sender = scanner.nextLine().trim();
        System.out.print("Enter Recipient Account Number: ");
        String recipient = scanner.nextLine().trim();
        System.out.print("Enter Transfer Amount ($): ");
        String amtStr = scanner.nextLine().trim();

        try {
            double amt = Double.parseDouble(amtStr);
            MoneyTransfer.transfer(sender, recipient, amt);
        } catch (NumberFormatException e) {
            System.err.println("\n[ERROR] Invalid amount entered. Must be a numeric value.");
        }
    }

    private static void handleTransactionHistory(Scanner scanner) {
        System.out.println("\n--- [TASK 5: TRANSACTION HISTORY] ---");
        System.out.print("Enter Account Number: ");
        String acc = scanner.nextLine().trim();
        TransactionHistory.displayHistory(acc);
    }
}
