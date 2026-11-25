package com.smartbank.fraud;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * A banking transaction as seen by the fraud detection module.
 *
 * <p>During integration, the Banking Operations module can either use this class directly or
 * convert its own transaction object into one of these before calling
 * {@link FraudDetection#analyze(Transaction)}.
 */
public final class Transaction {

    public enum Type {
        DEPOSIT("Deposit"),
        WITHDRAWAL("Withdrawal"),
        TRANSFER("Transfer");

        private final String label;

        Type(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    private final String transactionId;
    private final String accountNumber;
    private final String targetAccountNumber; // only used for transfers, otherwise null
    private final BigDecimal amount;
    private final Type type;
    private final LocalDateTime timestamp;

    public Transaction(String transactionId, String accountNumber, String targetAccountNumber,
                       BigDecimal amount, Type type, LocalDateTime timestamp) {
        this.transactionId = requireText(transactionId, "Transaction ID");
        this.accountNumber = requireText(accountNumber, "Account number");
        this.type = Objects.requireNonNull(type, "Transaction type is required");
        this.amount = Objects.requireNonNull(amount, "Amount is required");
        this.timestamp = Objects.requireNonNull(timestamp, "Timestamp is required");

        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        if (type == Type.TRANSFER) {
            this.targetAccountNumber = requireText(targetAccountNumber, "Target account for a transfer");
            if (this.targetAccountNumber.equals(this.accountNumber)) {
                throw new IllegalArgumentException("Cannot transfer money to the same account");
            }
        } else {
            this.targetAccountNumber = null;
        }
    }

    // ---------- Convenience factory methods ----------

    public static Transaction deposit(String id, String account, double amount, LocalDateTime time) {
        return new Transaction(id, account, null, BigDecimal.valueOf(amount), Type.DEPOSIT, time);
    }

    public static Transaction withdrawal(String id, String account, double amount, LocalDateTime time) {
        return new Transaction(id, account, null, BigDecimal.valueOf(amount), Type.WITHDRAWAL, time);
    }

    public static Transaction transfer(String id, String fromAccount, String toAccount,
                                       double amount, LocalDateTime time) {
        return new Transaction(id, fromAccount, toAccount, BigDecimal.valueOf(amount), Type.TRANSFER, time);
    }

    // ---------- Getters ----------

    public String getTransactionId() {
        return transactionId;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public String getTargetAccountNumber() {
        return targetAccountNumber;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Type getType() {
        return type;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof Transaction && transactionId.equals(((Transaction) o).transactionId));
    }

    @Override
    public int hashCode() {
        return transactionId.hashCode();
    }

    @Override
    public String toString() {
        return transactionId + " [" + type.getLabel() + " of " + Money.format(amount) + " from " + accountNumber
                + (targetAccountNumber != null ? " to " + targetAccountNumber : "") + "]";
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }
}
