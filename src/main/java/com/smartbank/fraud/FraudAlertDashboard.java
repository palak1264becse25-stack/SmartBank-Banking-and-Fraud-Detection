package com.smartbank.fraud;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Scanner;

/**
 * Console dashboard for the fraud module (Tasks 5 and 6).
 *
 * <p>Shows every monitored transaction with its fraud status, lists flagged alerts by risk,
 * shows the reasons for an alert, lets a reviewer mark alerts as reviewed or confirmed fraud,
 * and keeps the full alert history.
 *
 * <p>Run this class directly to start a demo loaded with sample transactions.
 */
public class FraudAlertDashboard {

    /** Set to false if your console shows codes like "[31m" instead of colours. */
    private static final boolean USE_COLORS = true;

    private static final String RESET = "\u001B[0m";
    private static final String BOLD = "\u001B[1m";
    private static final String RED = "\u001B[31m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String CYAN = "\u001B[36m";

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
    private static final int MAX_REASON_WIDTH = 50;
    private static final int BANNER_WIDTH = 64;
    private static final String NORMAL_LABEL = "Normal";
    private static final String NO_ISSUE_REASON = "No suspicious pattern";
    private static final String[] ALERT_HEADERS =
            {"Alert ID", "Transaction ID", "Account", "Amount", "Risk", "Reason", "Status"};

    private final FraudDetection detection;
    private final Scanner input;
    private int nextTransactionNumber = 201;

    public FraudAlertDashboard(FraudDetection detection, Scanner input) {
        this.detection = Objects.requireNonNull(detection, "detection");
        this.input = Objects.requireNonNull(input, "input");
        detection.addAlertListener(this::announceAlert);
    }

    // ============================================ Demo entry point ============================================

    public static void main(String[] args) {
        FraudDetection detection = new FraudDetection();
        List<Transaction> sample = sampleTransactions();
        int flagged = detection.analyzeAll(sample).size();

        FraudAlertDashboard dashboard = new FraudAlertDashboard(detection, new Scanner(System.in));
        dashboard.start("Loaded " + sample.size() + " sample transactions: " + flagged + " were flagged for review.");
    }

    /** Opens the dashboard menu and runs until the user chooses Exit. */
    public void start(String welcomeMessage) {
        printBanner();
        if (welcomeMessage != null) {
            System.out.println(welcomeMessage);
        }
        while (true) {
            printMenu();
            String choice = prompt("Choose an option");
            if (choice == null || choice.equals("0")) {
                System.out.println("Closing the Fraud Alert Dashboard. Goodbye!");
                return;
            }
            System.out.println();
            try {
                switch (choice) {
                    case "1" -> showAllTransactions();
                    case "2" -> showFlaggedAlerts();
                    case "3" -> showAlertDetails();
                    case "4" -> reviewAlert(false);
                    case "5" -> reviewAlert(true);
                    case "6" -> showAlertHistory();
                    case "7" -> showSummary();
                    case "8" -> simulateTransaction();
                    case "9" -> showRules();
                    default -> System.out.println(color(RED, "Invalid option. Please enter a number from 0 to 9."));
                }
            } catch (IllegalArgumentException | IllegalStateException e) {
                System.out.println(color(RED, "Error: " + e.getMessage()));
            }
        }
    }

    // ============================================ Menu actions ============================================

    /** Task 5: every monitored transaction with its amount, reason and status. */
    private void showAllTransactions() {
        printTitle("ALL MONITORED TRANSACTIONS");
        List<String[]> rows = new ArrayList<>();
        for (Transaction tx : detection.getAnalysedTransactions()) {
            Optional<FraudAlert> alert = detection.findAlertForTransaction(tx.getTransactionId());
            rows.add(new String[] {
                    tx.getTransactionId(),
                    tx.getAccountNumber(),
                    tx.getType().getLabel(),
                    Money.format(tx.getAmount()),
                    shorten(alert.map(FraudAlert::getReasonSummary).orElse(NO_ISSUE_REASON)),
                    alert.map(a -> a.getStatus().getLabel()).orElse(NORMAL_LABEL)
            });
        }
        printTable(new String[] {"Transaction ID", "Account", "Type", "Amount", "Reason", "Status"}, rows, 5);
    }

    /** Task 6: open alerts, with the highest risk first so the most dangerous ones are reviewed first. */
    private void showFlaggedAlerts() {
        printTitle("FLAGGED ALERTS  (highest risk first)");
        List<FraudAlert> open = new ArrayList<>(detection.getFlaggedAlerts());
        open.sort(Comparator.comparingInt(FraudAlert::getRiskScore).reversed()
                .thenComparing(FraudAlert::getCreatedAt));
        List<String[]> rows = new ArrayList<>();
        for (FraudAlert alert : open) {
            rows.add(alertRow(alert));
        }
        printTable(ALERT_HEADERS, rows, 6);
    }

    /** Task 6: full details of one alert, including every reason and its status history. */
    private void showAlertDetails() {
        FraudAlert alert = askForAlert();
        if (alert != null) {
            printAlertCard(alert);
        }
    }

    /** Task 6: mark an alert as reviewed (genuine) or confirm it as fraud. */
    private void reviewAlert(boolean confirmFraud) {
        if (detection.getFlaggedAlerts().isEmpty()) {
            System.out.println(color(GREEN, "There are no open alerts to review."));
            return;
        }
        showFlaggedAlerts();
        FraudAlert alert = askForAlert();
        if (alert == null) {
            return;
        }
        if (!alert.isOpen()) {
            System.out.println(color(YELLOW, "Alert " + alert.getAlertId() + " is already marked as "
                    + alert.getStatus().getLabel() + "."));
            return;
        }

        String reviewer = prompt("Reviewed by (your name)");
        if (isBlank(reviewer)) {
            reviewer = "Admin";
        }
        String note = prompt(confirmFraud ? "Reason for confirming fraud" : "Review note (optional)");

        if (confirmFraud) {
            String answer = prompt("Type YES to confirm " + alert.getAlertId() + " as fraud");
            if (!"YES".equalsIgnoreCase(answer == null ? "" : answer.trim())) {
                System.out.println("Cancelled. The alert is still open.");
                return;
            }
            detection.confirmFraud(alert.getAlertId(), reviewer, note);
            System.out.println(color(RED + BOLD, "Alert " + alert.getAlertId() + " confirmed as fraud. Account "
                    + alert.getAccountNumber() + " should be investigated."));
        } else {
            detection.markReviewed(alert.getAlertId(), reviewer, note);
            System.out.println(color(GREEN, "Alert " + alert.getAlertId() + " marked as reviewed by " + reviewer + "."));
        }
    }

    /** Task 6: every alert ever raised, including closed ones, with its latest action. */
    private void showAlertHistory() {
        printTitle("ALERT HISTORY  (all alerts)");
        List<String[]> rows = new ArrayList<>();
        for (FraudAlert alert : detection.getAllAlerts()) {
            FraudAlert.StatusChange last = alert.getLastChange();
            String action = alert.isOpen()
                    ? "Waiting for review"
                    : last.getStatus().getLabel() + " by " + last.getChangedBy()
                      + (last.getNote().isEmpty() ? "" : ": " + last.getNote());
            rows.add(new String[] {
                    alert.getAlertId(),
                    alert.getTransactionId(),
                    Money.format(alert.getAmount()),
                    alert.getCreatedAt().format(DATE_TIME),
                    alert.getStatus().getLabel(),
                    shorten(action)
            });
        }
        printTable(new String[] {"Alert ID", "Transaction ID", "Amount", "Raised at", "Status", "Last action"},
                rows, 4);
        System.out.println("Tip: choose option 3 to see the full timeline of a single alert.");
    }

    /** Overall statistics, useful for a quick report. */
    private void showSummary() {
        printTitle("FRAUD SUMMARY REPORT");
        List<Transaction> transactions = detection.getAnalysedTransactions();
        List<FraudAlert> alerts = detection.getAllAlerts();

        Map<FraudAlert.Status, Integer> byStatus = new EnumMap<>(FraudAlert.Status.class);
        Map<FraudAlert.RiskLevel, Integer> byRisk = new EnumMap<>(FraudAlert.RiskLevel.class);
        Map<String, Integer> byAccount = new HashMap<>();
        BigDecimal amountUnderAlert = BigDecimal.ZERO;
        for (FraudAlert alert : alerts) {
            byStatus.merge(alert.getStatus(), 1, Integer::sum);
            byRisk.merge(alert.getRiskLevel(), 1, Integer::sum);
            byAccount.merge(alert.getAccountNumber(), 1, Integer::sum);
            amountUnderAlert = amountUnderAlert.add(alert.getAmount());
        }
        double flagRate = transactions.isEmpty() ? 0 : alerts.size() * 100.0 / transactions.size();

        field("Transactions monitored", String.valueOf(transactions.size()));
        field("Normal transactions", String.valueOf(transactions.size() - alerts.size()));
        field("Alerts raised", alerts.size() + String.format(Locale.ROOT, "  (%.1f%% of transactions)", flagRate));
        field("  Open (Flagged)", color(RED, String.valueOf(byStatus.getOrDefault(FraudAlert.Status.FLAGGED, 0))));
        field("  Reviewed", color(YELLOW, String.valueOf(byStatus.getOrDefault(FraudAlert.Status.REVIEWED, 0))));
        field("  Confirmed fraud",
                color(RED + BOLD, String.valueOf(byStatus.getOrDefault(FraudAlert.Status.CONFIRMED_FRAUD, 0))));
        field("Amount under alert", Money.format(amountUnderAlert));
        field("Risk  High / Medium / Low", byRisk.getOrDefault(FraudAlert.RiskLevel.HIGH, 0) + " / "
                + byRisk.getOrDefault(FraudAlert.RiskLevel.MEDIUM, 0) + " / "
                + byRisk.getOrDefault(FraudAlert.RiskLevel.LOW, 0));
        byAccount.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .ifPresent(e -> field("Most flagged account", e.getKey() + "  (" + e.getValue() + " alert"
                        + (e.getValue() == 1 ? "" : "s") + ")"));
    }

    /** Lets the user type in a transaction (optionally repeated) and see the fraud rules react live. */
    private void simulateTransaction() {
        printTitle("SIMULATE A NEW TRANSACTION");
        String account = prompt("Account number (e.g. ACC1001)");
        if (isBlank(account)) {
            return;
        }
        Transaction.Type type = askType();
        if (type == null) {
            return;
        }
        String target = null;
        if (type == Transaction.Type.TRANSFER) {
            target = prompt("Target account number");
            if (isBlank(target)) {
                return;
            }
            target = target.trim().toUpperCase(Locale.ROOT);
        }
        BigDecimal amount = askAmount();
        if (amount == null) {
            return;
        }
        int repeat = askCount("How many times should it be sent? (Enter = 1, max 20)", 20);

        LocalDateTime start = LocalDateTime.now();
        for (int i = 0; i < repeat; i++) {
            Transaction tx = new Transaction(nextTransactionId(), account.trim().toUpperCase(Locale.ROOT), target,
                    amount, type, start.plusSeconds(i));
            if (detection.analyze(tx).isEmpty()) {
                System.out.println(color(GREEN, "  " + tx.getTransactionId() + "  " + Money.format(amount)
                        + "  -> Normal (passed all fraud checks)"));
            }
            // Flagged transactions are printed by announceAlert(), which listens for new alerts.
        }
    }

    private void showRules() {
        printTitle("ACTIVE FRAUD RULES");
        for (String line : detection.getRules().describe()) {
            System.out.println("  - " + line);
        }
        System.out.println("Risk score = sum of points for every rule broken (max 100).  "
                + "High >= 70, Medium >= 40, Low < 40.");
    }

    // ============================================ Display helpers ============================================

    private void announceAlert(FraudAlert alert) {
        System.out.println(color(RED + BOLD, "  !! FRAUD ALERT " + alert.getAlertId() + "  " + alert.getTransactionId()
                + "  " + alert.getAccountNumber() + "  " + Money.format(alert.getAmount())
                + "  [risk " + alert.getRiskScore() + ", " + alert.getRiskLevel() + "]"));
        System.out.println(color(RED, "     Reason: " + alert.getReasonSummary()));
    }

    private void printAlertCard(FraudAlert alert) {
        String line = "-".repeat(BANNER_WIDTH);
        String status = alert.getStatus().getLabel();
        System.out.println(line);
        System.out.println(color(BOLD, " Alert " + alert.getAlertId()) + "      Status: "
                + color(statusColor(status), status));
        System.out.println(line);
        field("Transaction ID", alert.getTransactionId());
        field("Account", alert.getAccountNumber());
        field("Amount", Money.format(alert.getAmount()));
        field("Transaction time", alert.getTransactionTime().format(DATE_TIME));
        field("Alert raised", alert.getCreatedAt().format(DATE_TIME));
        field("Risk score", alert.getRiskScore() + "/100 (" + alert.getRiskLevel() + ")");
        System.out.println(" Reason(s) for alert:");
        int number = 1;
        for (String reason : alert.getReasons()) {
            System.out.println("   " + number++ + ". " + reason);
        }
        System.out.println(" Status history:");
        for (FraudAlert.StatusChange change : alert.getHistory()) {
            System.out.println("   " + change.getChangedAt().format(DATE_TIME) + "  "
                    + pad(change.getStatus().getLabel(), 16) + "by " + change.getChangedBy()
                    + (change.getNote().isEmpty() ? "" : "  - " + change.getNote()));
        }
        System.out.println(line);
    }

    private String[] alertRow(FraudAlert alert) {
        return new String[] {
                alert.getAlertId(),
                alert.getTransactionId(),
                alert.getAccountNumber(),
                Money.format(alert.getAmount()),
                alert.getRiskScore() + " " + alert.getRiskLevel(),
                shorten(alert.getReasonSummary()),
                alert.getStatus().getLabel()
        };
    }

    /** Prints a bordered table. Column widths adjust to the content; the status column is coloured. */
    private void printTable(String[] headers, List<String[]> rows, int statusColumn) {
        int[] widths = new int[headers.length];
        for (int i = 0; i < headers.length; i++) {
            widths[i] = headers[i].length();
        }
        for (String[] row : rows) {
            for (int i = 0; i < row.length; i++) {
                widths[i] = Math.max(widths[i], row[i].length());
            }
        }

        StringBuilder border = new StringBuilder("+");
        for (int width : widths) {
            border.append("-".repeat(width + 2)).append('+');
        }

        System.out.println(border);
        System.out.println(formatRow(headers, widths, -1, true));
        System.out.println(border);
        if (rows.isEmpty()) {
            System.out.println("| " + pad("No records to show.", border.length() - 4) + " |");
        }
        for (String[] row : rows) {
            System.out.println(formatRow(row, widths, statusColumn, false));
        }
        System.out.println(border);
    }

    private String formatRow(String[] cells, int[] widths, int statusColumn, boolean header) {
        StringBuilder sb = new StringBuilder("|");
        for (int i = 0; i < cells.length; i++) {
            String cell = " " + pad(cells[i], widths[i]) + " ";
            if (header) {
                cell = color(BOLD, cell);
            } else if (i == statusColumn) {
                cell = color(statusColor(cells[i]), cell);
            }
            sb.append(cell).append('|');
        }
        return sb.toString();
    }

    private static String statusColor(String statusLabel) {
        if (statusLabel.equals(FraudAlert.Status.FLAGGED.getLabel())) {
            return RED;
        }
        if (statusLabel.equals(FraudAlert.Status.REVIEWED.getLabel())) {
            return YELLOW;
        }
        if (statusLabel.equals(FraudAlert.Status.CONFIRMED_FRAUD.getLabel())) {
            return RED + BOLD;
        }
        return GREEN;
    }

    private void printBanner() {
        String line = "=".repeat(BANNER_WIDTH);
        String title = "SMARTBANK  |  FRAUD ALERT DASHBOARD";
        String centred = " ".repeat((BANNER_WIDTH - title.length()) / 2) + title;
        System.out.println(color(CYAN, line));
        System.out.println(color(CYAN + BOLD, centred));
        System.out.println(color(CYAN, line));
    }

    private void printMenu() {
        int open = detection.getFlaggedAlerts().size();
        System.out.println();
        System.out.println(color(BOLD, "MAIN MENU") + "   Open alerts: "
                + color(open > 0 ? RED : GREEN, String.valueOf(open)));
        System.out.println("  1. View all monitored transactions");
        System.out.println("  2. View flagged alerts");
        System.out.println("  3. View alert details and reason");
        System.out.println("  4. Mark alert as reviewed");
        System.out.println("  5. Confirm alert as fraud");
        System.out.println("  6. View alert history");
        System.out.println("  7. Fraud summary report");
        System.out.println("  8. Simulate a new transaction");
        System.out.println("  9. View fraud rules");
        System.out.println("  0. Exit");
    }

    private void printTitle(String title) {
        System.out.println(color(CYAN + BOLD, "== " + title + " =="));
    }

    private static void field(String name, String value) {
        System.out.println(" " + pad(name, 26) + ": " + value);
    }

    private static String color(String code, String text) {
        return USE_COLORS ? code + text + RESET : text;
    }

    private static String pad(String text, int width) {
        return text + " ".repeat(Math.max(0, width - text.length()));
    }

    private static String shorten(String text) {
        return text.length() <= MAX_REASON_WIDTH ? text : text.substring(0, MAX_REASON_WIDTH - 3) + "...";
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }

    // ============================================ Input helpers ============================================

    /** Reads one line. Returns null when there is no more input (for example the console was closed). */
    private String prompt(String label) {
        System.out.print(label + ": ");
        if (!input.hasNextLine()) {
            System.out.println();
            return null;
        }
        return input.nextLine().trim();
    }

    private FraudAlert askForAlert() {
        String id = prompt("Enter Alert ID or Transaction ID (e.g. FA-0001 or TX101)");
        if (isBlank(id)) {
            return null;
        }
        Optional<FraudAlert> alert = detection.findAlert(id);
        if (alert.isEmpty()) {
            String extra = detection.findTransaction(id).isPresent()
                    ? " This transaction passed all fraud checks." : "";
            System.out.println(color(YELLOW, "No alert found for '" + id + "'." + extra));
            return null;
        }
        return alert.get();
    }

    private Transaction.Type askType() {
        String choice = prompt("Type (1 = Deposit, 2 = Withdrawal, 3 = Transfer)");
        if (choice == null) {
            return null;
        }
        switch (choice) {
            case "1":
                return Transaction.Type.DEPOSIT;
            case "2":
                return Transaction.Type.WITHDRAWAL;
            case "3":
                return Transaction.Type.TRANSFER;
            default:
                System.out.println(color(RED, "Invalid type. Please enter 1, 2 or 3."));
                return null;
        }
    }

    private BigDecimal askAmount() {
        String text = prompt("Amount in rupees (e.g. 60000)");
        if (isBlank(text)) {
            return null;
        }
        try {
            BigDecimal amount = new BigDecimal(text.replace(",", "").replace("₹", "").trim());
            if (amount.signum() <= 0) {
                System.out.println(color(RED, "Amount must be greater than zero."));
                return null;
            }
            return amount;
        } catch (NumberFormatException e) {
            System.out.println(color(RED, "'" + text + "' is not a valid amount."));
            return null;
        }
    }

    private int askCount(String label, int max) {
        String text = prompt(label);
        if (isBlank(text)) {
            return 1;
        }
        try {
            int value = Integer.parseInt(text);
            if (value >= 1 && value <= max) {
                return value;
            }
        } catch (NumberFormatException ignored) {
            // fall through to the message below
        }
        System.out.println(color(YELLOW, "Please enter a number from 1 to " + max + ". Sending it once."));
        return 1;
    }

    private String nextTransactionId() {
        String id;
        do {
            id = "TX" + nextTransactionNumber++;
        } while (detection.findTransaction(id).isPresent());
        return id;
    }

    // ============================================ Sample data ============================================

    /**
     * Sample transactions that show off every rule. Times are relative to "now" so that
     * transactions you simulate during the demo are compared against a realistic recent history.
     */
    static List<Transaction> sampleTransactions() {
        LocalDateTime base = LocalDateTime.now().withSecond(0).withNano(0).minusHours(3);
        List<Transaction> list = new ArrayList<>();

        // ACC1001: one large deposit -> high-value rule (matches TX101 in the brief)
        list.add(Transaction.deposit("TX101", "ACC1001", 60_000, base.plusMinutes(120)));

        // ACC1002: ordinary activity -> normal (matches TX102 in the brief)
        list.add(Transaction.withdrawal("TX102", "ACC1002", 5_000, base.plusMinutes(121)));
        list.add(Transaction.deposit("TX112", "ACC1002", 15_000, base.plusMinutes(140)));

        // ACC1003: the same ₹25,000 transfer three times in 8 minutes -> repeated transactions (matches TX103)
        list.add(Transaction.transfer("TX097", "ACC1003", "ACC2001", 25_000, base.plusMinutes(112)));
        list.add(Transaction.transfer("TX098", "ACC1003", "ACC2001", 25_000, base.plusMinutes(116)));
        list.add(Transaction.transfer("TX103", "ACC1003", "ACC2001", 25_000, base.plusMinutes(120)));

        // ACC1004: four small transactions in four minutes -> high frequency
        list.add(Transaction.withdrawal("TX104", "ACC1004", 1_200, base.plusMinutes(125)));
        list.add(Transaction.withdrawal("TX105", "ACC1004", 800, base.plusMinutes(126)));
        list.add(Transaction.transfer("TX106", "ACC1004", "ACC2003", 1_500, base.plusMinutes(127)));
        list.add(Transaction.withdrawal("TX107", "ACC1004", 2_000, base.plusMinutes(128)));

        // ACC1005: usually around ₹2,000, then ₹18,000 -> unusual pattern
        list.add(Transaction.deposit("TX108", "ACC1005", 2_000, base.plusMinutes(60)));
        list.add(Transaction.withdrawal("TX109", "ACC1005", 1_500, base.plusMinutes(90)));
        list.add(Transaction.deposit("TX110", "ACC1005", 2_500, base.plusMinutes(105)));
        list.add(Transaction.withdrawal("TX111", "ACC1005", 18_000, base.plusMinutes(135)));

        // ACC1006: six transfers within one hour -> frequent transfers
        double[] transferAmounts = {3_000, 3_500, 2_800, 4_000, 3_200, 3_900};
        for (int i = 0; i < transferAmounts.length; i++) {
            list.add(Transaction.transfer("TX09" + i, "ACC1006", "ACC2002", transferAmounts[i],
                    base.plusMinutes(70 + i * 10L)));
        }

        // ACC1008: small history, then ₹95,000 transfer -> high-value + unusual = HIGH risk
        list.add(Transaction.deposit("TX085", "ACC1008", 1_000, base));
        list.add(Transaction.deposit("TX086", "ACC1008", 1_200, base.plusMinutes(30)));
        list.add(Transaction.withdrawal("TX087", "ACC1008", 900, base.plusMinutes(60)));
        list.add(Transaction.transfer("TX114", "ACC1008", "ACC3001", 95_000, base.plusMinutes(160)));

        return list;
    }
}
