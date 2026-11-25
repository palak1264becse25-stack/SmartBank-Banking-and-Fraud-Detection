package com.smartbank.fraud;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The fraud detection rules and all of their thresholds.
 *
 * <p>Every limit lives in this one class so the team can tune the system in a single place,
 * either by editing the defaults below or at runtime with the setters:
 * <pre>
 *     FraudRules rules = new FraudRules()
 *             .setHighValueLimit(75_000)
 *             .setFrequencyRule(5, Duration.ofMinutes(10));
 * </pre>
 *
 * <p>New rules can be plugged in with {@link #addRule(Rule)} without changing the detection engine.
 */
public class FraudRules {

    /** One fraud check. Returns a {@link Violation} when the transaction breaks the rule, otherwise {@code null}. */
    @FunctionalInterface
    public interface Rule {
        Violation check(Transaction tx, TransactionMonitor monitor);
    }

    /** Why a transaction was flagged and how much risk that adds to the alert. */
    public static final class Violation {
        private final String ruleName;
        private final String reason;
        private final int riskPoints;

        public Violation(String ruleName, String reason, int riskPoints) {
            this.ruleName = Objects.requireNonNull(ruleName);
            this.reason = Objects.requireNonNull(reason);
            this.riskPoints = riskPoints;
        }

        public String getRuleName() {
            return ruleName;
        }

        public String getReason() {
            return reason;
        }

        public int getRiskPoints() {
            return riskPoints;
        }

        @Override
        public String toString() {
            return reason;
        }
    }

    // ================= Default thresholds (edit these to tune the system) =================

    private BigDecimal highValueLimit = new BigDecimal("50000");

    private int maxTransactionsInWindow = 3;
    private Duration frequencyWindow = Duration.ofMinutes(5);

    private int maxSameAmountInWindow = 2;
    private Duration repeatWindow = Duration.ofMinutes(10);

    private int maxTransfersInWindow = 5;
    private Duration transferWindow = Duration.ofHours(1);

    private double unusualAmountMultiplier = 5.0;
    private int minHistoryForPatternCheck = 3;

    // Risk points added to an alert's score (0-100) when a rule is broken.
    private static final int HIGH_VALUE_POINTS = 50;
    private static final int FREQUENCY_POINTS = 30;
    private static final int REPEATED_POINTS = 35;
    private static final int TRANSFER_POINTS = 25;
    private static final int UNUSUAL_POINTS = 25;

    // ========================================================================================

    private final List<Rule> rules = new ArrayList<>();
    private int customRuleCount;

    public FraudRules() {
        rules.add(this::checkHighValue);
        rules.add(this::checkFrequency);
        rules.add(this::checkRepeatedAmount);
        rules.add(this::checkTransferFrequency);
        rules.add(this::checkUnusualAmount);
    }

    /** Runs every rule against the transaction and returns all rules it breaks (empty if it is normal). */
    public List<Violation> evaluate(Transaction tx, TransactionMonitor monitor) {
        List<Violation> violations = new ArrayList<>();
        for (Rule rule : rules) {
            Violation violation = rule.check(tx, monitor);
            if (violation != null) {
                violations.add(violation);
            }
        }
        return violations;
    }

    /** Adds an extra rule, for example one written by another team member. */
    public FraudRules addRule(Rule rule) {
        rules.add(Objects.requireNonNull(rule, "rule"));
        customRuleCount++;
        return this;
    }

    // ---------------------------------- The rules ----------------------------------

    /** Task 2: flags any transaction above the high-value limit. */
    private Violation checkHighValue(Transaction tx, TransactionMonitor monitor) {
        if (tx.getAmount().compareTo(highValueLimit) <= 0) {
            return null;
        }
        return new Violation("HIGH_VALUE",
                "High-value transaction (above " + Money.format(highValueLimit) + ")", HIGH_VALUE_POINTS);
    }

    /** Task 3: flags too many transactions from one account in a short period. */
    private Violation checkFrequency(Transaction tx, TransactionMonitor monitor) {
        int count = monitor.countInWindow(tx.getAccountNumber(), frequencyWindow, tx.getTimestamp());
        if (count <= maxTransactionsInWindow) {
            return null;
        }
        return new Violation("HIGH_FREQUENCY",
                "High frequency: " + count + " transactions within " + formatDuration(frequencyWindow),
                FREQUENCY_POINTS);
    }

    /** Task 3: flags the same amount being repeated again and again. */
    private Violation checkRepeatedAmount(Transaction tx, TransactionMonitor monitor) {
        int count = monitor.countSameAmount(tx.getAccountNumber(), tx.getAmount(), repeatWindow, tx.getTimestamp());
        if (count <= maxSameAmountInWindow) {
            return null;
        }
        return new Violation("REPEATED_TRANSACTIONS",
                "Repeated transactions: " + Money.format(tx.getAmount()) + " sent " + count
                        + " times within " + formatDuration(repeatWindow),
                REPEATED_POINTS);
    }

    /** Task 3: flags unusually frequent transfers from one account. */
    private Violation checkTransferFrequency(Transaction tx, TransactionMonitor monitor) {
        if (tx.getType() != Transaction.Type.TRANSFER) {
            return null;
        }
        int count = monitor.countByType(tx.getAccountNumber(), Transaction.Type.TRANSFER,
                transferWindow, tx.getTimestamp());
        if (count <= maxTransfersInWindow) {
            return null;
        }
        return new Violation("FREQUENT_TRANSFERS",
                "Frequent transfers: " + count + " transfers within " + formatDuration(transferWindow),
                TRANSFER_POINTS);
    }

    /** Task 1: flags an amount far above what this account normally does. */
    private Violation checkUnusualAmount(Transaction tx, TransactionMonitor monitor) {
        List<Transaction> previous = monitor.getHistoryBefore(tx);
        if (previous.size() < minHistoryForPatternCheck) {
            return null; // not enough history to know what "normal" looks like
        }
        BigDecimal total = BigDecimal.ZERO;
        for (Transaction t : previous) {
            total = total.add(t.getAmount());
        }
        BigDecimal average = total.divide(BigDecimal.valueOf(previous.size()), 2, RoundingMode.HALF_UP);
        BigDecimal limit = average.multiply(BigDecimal.valueOf(unusualAmountMultiplier));
        if (tx.getAmount().compareTo(limit) <= 0) {
            return null;
        }
        BigDecimal times = tx.getAmount().divide(average, 1, RoundingMode.HALF_UP);
        return new Violation("UNUSUAL_PATTERN",
                "Unusual amount: " + times + "x this account's average of "
                        + Money.format(average.setScale(0, RoundingMode.HALF_UP)),
                UNUSUAL_POINTS);
    }

    // ---------------------------------- Configuration ----------------------------------

    public FraudRules setHighValueLimit(double limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("High-value limit must be positive");
        }
        this.highValueLimit = BigDecimal.valueOf(limit);
        return this;
    }

    public FraudRules setFrequencyRule(int maxTransactions, Duration window) {
        this.maxTransactionsInWindow = requirePositive(maxTransactions, "Max transactions");
        this.frequencyWindow = requirePositive(window);
        return this;
    }

    public FraudRules setRepeatRule(int maxSameAmount, Duration window) {
        this.maxSameAmountInWindow = requirePositive(maxSameAmount, "Max repeated amount");
        this.repeatWindow = requirePositive(window);
        return this;
    }

    public FraudRules setTransferRule(int maxTransfers, Duration window) {
        this.maxTransfersInWindow = requirePositive(maxTransfers, "Max transfers");
        this.transferWindow = requirePositive(window);
        return this;
    }

    public FraudRules setUnusualAmountRule(double multiplier, int minHistory) {
        if (multiplier <= 1) {
            throw new IllegalArgumentException("Multiplier must be greater than 1");
        }
        this.unusualAmountMultiplier = multiplier;
        this.minHistoryForPatternCheck = requirePositive(minHistory, "Minimum history");
        return this;
    }

    public BigDecimal getHighValueLimit() {
        return highValueLimit;
    }

    /** A readable list of the active rules, used by the dashboard. */
    public List<String> describe() {
        List<String> lines = new ArrayList<>();
        lines.add("High-value      : amount above " + Money.format(highValueLimit)
                + "  (+" + HIGH_VALUE_POINTS + " risk)");
        lines.add("Frequency       : more than " + maxTransactionsInWindow + " transactions from one account within "
                + formatDuration(frequencyWindow) + "  (+" + FREQUENCY_POINTS + " risk)");
        lines.add("Repeated amount : same amount more than " + maxSameAmountInWindow + " times within "
                + formatDuration(repeatWindow) + "  (+" + REPEATED_POINTS + " risk)");
        lines.add("Transfers       : more than " + maxTransfersInWindow + " transfers from one account within "
                + formatDuration(transferWindow) + "  (+" + TRANSFER_POINTS + " risk)");
        lines.add("Unusual pattern : amount more than "
                + BigDecimal.valueOf(unusualAmountMultiplier).stripTrailingZeros().toPlainString()
                + "x the account's average (needs " + minHistoryForPatternCheck + "+ past transactions)  (+"
                + UNUSUAL_POINTS + " risk)");
        if (customRuleCount > 0) {
            lines.add("Custom rules    : " + customRuleCount + " additional rule(s) registered");
        }
        return lines;
    }

    // ---------------------------------- Helpers ----------------------------------

    static String formatDuration(Duration d) {
        long minutes = d.toMinutes();
        if (minutes >= 60 && minutes % 60 == 0) {
            long hours = minutes / 60;
            return hours + (hours == 1 ? " hour" : " hours");
        }
        if (minutes >= 1) {
            return minutes + (minutes == 1 ? " minute" : " minutes");
        }
        long seconds = d.getSeconds();
        return seconds + (seconds == 1 ? " second" : " seconds");
    }

    private static int requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private static Duration requirePositive(Duration window) {
        if (window == null || window.isNegative() || window.isZero()) {
            throw new IllegalArgumentException("Time window must be positive");
        }
        return window;
    }
}
