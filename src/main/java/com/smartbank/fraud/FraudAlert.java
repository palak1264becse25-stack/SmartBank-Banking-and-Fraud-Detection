package com.smartbank.fraud;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * A fraud alert raised for a suspicious transaction (Task 4).
 *
 * <p>It stores the transaction ID, account number, amount, reason(s) for flagging, the date and
 * time, a risk score and a status. Every status change is kept in the alert's history (Task 6).
 */
public class FraudAlert {

    public enum Status {
        FLAGGED("Flagged"),
        REVIEWED("Reviewed"),
        CONFIRMED_FRAUD("Confirmed fraud");

        private final String label;

        Status(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    public enum RiskLevel {
        LOW, MEDIUM, HIGH;

        public static RiskLevel fromScore(int score) {
            if (score >= 70) {
                return HIGH;
            }
            return score >= 40 ? MEDIUM : LOW;
        }
    }

    /** One entry in the alert's history: what changed, when, by whom and why. */
    public static final class StatusChange {
        private final Status status;
        private final LocalDateTime changedAt;
        private final String changedBy;
        private final String note;

        public StatusChange(Status status, LocalDateTime changedAt, String changedBy, String note) {
            this.status = Objects.requireNonNull(status);
            this.changedAt = Objects.requireNonNull(changedAt);
            this.changedBy = changedBy;
            this.note = note == null ? "" : note.trim();
        }

        public Status getStatus() {
            return status;
        }

        public LocalDateTime getChangedAt() {
            return changedAt;
        }

        public String getChangedBy() {
            return changedBy;
        }

        public String getNote() {
            return note;
        }
    }

    private final String alertId;
    private final String transactionId;
    private final String accountNumber;
    private final BigDecimal amount;
    private final LocalDateTime transactionTime;
    private final List<String> reasons;
    private final int riskScore;
    private final LocalDateTime createdAt;
    private final List<StatusChange> history = new ArrayList<>();
    private Status status;

    public FraudAlert(String alertId, String transactionId, String accountNumber, BigDecimal amount,
                      LocalDateTime transactionTime, List<String> reasons, int riskScore, LocalDateTime createdAt) {
        this.alertId = Objects.requireNonNull(alertId, "alertId");
        this.transactionId = Objects.requireNonNull(transactionId, "transactionId");
        this.accountNumber = Objects.requireNonNull(accountNumber, "accountNumber");
        this.amount = Objects.requireNonNull(amount, "amount");
        this.transactionTime = Objects.requireNonNull(transactionTime, "transactionTime");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        if (reasons == null || reasons.isEmpty()) {
            throw new IllegalArgumentException("An alert needs at least one reason");
        }
        this.reasons = List.copyOf(reasons);
        this.riskScore = Math.max(0, Math.min(100, riskScore));
        this.status = Status.FLAGGED;
        history.add(new StatusChange(Status.FLAGGED, createdAt, "System", "Raised automatically by fraud rules"));
    }

    /** Builds an alert from the rules a transaction broke. Risk points are added up into the risk score. */
    static FraudAlert fromViolations(String alertId, Transaction tx, List<FraudRules.Violation> violations,
                                     LocalDateTime createdAt) {
        List<String> reasons = violations.stream()
                .map(FraudRules.Violation::getReason)
                .collect(Collectors.toList());
        int score = violations.stream().mapToInt(FraudRules.Violation::getRiskPoints).sum();
        return new FraudAlert(alertId, tx.getTransactionId(), tx.getAccountNumber(), tx.getAmount(),
                tx.getTimestamp(), reasons, score, createdAt);
    }

    /** Marks the alert as checked by a reviewer and found to be genuine activity. */
    public void markReviewed(String reviewer, String note) {
        changeStatus(Status.REVIEWED, reviewer, note);
    }

    /** Marks the alert as checked by a reviewer and confirmed to be fraud. */
    public void confirmFraud(String reviewer, String note) {
        changeStatus(Status.CONFIRMED_FRAUD, reviewer, note);
    }

    private void changeStatus(Status newStatus, String reviewer, String note) {
        if (status != Status.FLAGGED) {
            throw new IllegalStateException("Alert " + alertId + " is already marked as " + status.getLabel());
        }
        String by = (reviewer == null || reviewer.isBlank()) ? "Unknown" : reviewer.trim();
        status = newStatus;
        history.add(new StatusChange(newStatus, LocalDateTime.now(), by, note));
    }

    /** True while the alert still needs a reviewer to look at it. */
    public boolean isOpen() {
        return status == Status.FLAGGED;
    }

    public String getReasonSummary() {
        return String.join("; ", reasons);
    }

    public RiskLevel getRiskLevel() {
        return RiskLevel.fromScore(riskScore);
    }

    public StatusChange getLastChange() {
        return history.get(history.size() - 1);
    }

    // ---------- Getters ----------

    public String getAlertId() {
        return alertId;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDateTime getTransactionTime() {
        return transactionTime;
    }

    public List<String> getReasons() {
        return reasons;
    }

    public int getRiskScore() {
        return riskScore;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public Status getStatus() {
        return status;
    }

    public List<StatusChange> getHistory() {
        return Collections.unmodifiableList(history);
    }

    @Override
    public String toString() {
        return alertId + " [" + transactionId + ", " + accountNumber + ", " + Money.format(amount) + ", "
                + status.getLabel() + ", risk " + riskScore + "] " + getReasonSummary();
    }
}
