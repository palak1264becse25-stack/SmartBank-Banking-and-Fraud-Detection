package com.smartbank.fraud;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Keeps a recent history of transactions for every account so that fraud rules can look at
 * patterns over time (frequency, repeated amounts, transfer bursts, unusual amounts).
 *
 * <p>Old transactions are removed automatically once they are older than the retention period,
 * so memory use stays small even when the bank processes many transactions.
 */
public class TransactionMonitor {

    /** How long transactions are kept for pattern analysis. */
    public static final Duration DEFAULT_RETENTION = Duration.ofDays(30);

    private final Map<String, Deque<Transaction>> historyByAccount = new HashMap<>();
    private final Duration retention;
    private int totalRecorded;

    public TransactionMonitor() {
        this(DEFAULT_RETENTION);
    }

    public TransactionMonitor(Duration retention) {
        if (retention == null || retention.isNegative() || retention.isZero()) {
            throw new IllegalArgumentException("Retention period must be positive");
        }
        this.retention = retention;
    }

    /** Adds a transaction to its account's history and drops entries older than the retention period. */
    public void record(Transaction tx) {
        Objects.requireNonNull(tx, "transaction");
        Deque<Transaction> history = historyByAccount.computeIfAbsent(tx.getAccountNumber(), k -> new ArrayDeque<>());
        history.addLast(tx);
        totalRecorded++;

        LocalDateTime cutoff = tx.getTimestamp().minus(retention);
        while (!history.isEmpty() && history.peekFirst().getTimestamp().isBefore(cutoff)) {
            history.pollFirst();
        }
    }

    /** Number of transactions made by the account in the time window ending at {@code at}. */
    public int countInWindow(String accountNumber, Duration window, LocalDateTime at) {
        return (int) inWindow(accountNumber, window, at).count();
    }

    /** Number of transactions with exactly this amount made by the account in the time window. */
    public int countSameAmount(String accountNumber, BigDecimal amount, Duration window, LocalDateTime at) {
        return (int) inWindow(accountNumber, window, at)
                .filter(t -> t.getAmount().compareTo(amount) == 0)
                .count();
    }

    /** Number of transactions of a given type (for example transfers) made by the account in the time window. */
    public int countByType(String accountNumber, Transaction.Type type, Duration window, LocalDateTime at) {
        return (int) inWindow(accountNumber, window, at)
                .filter(t -> t.getType() == type)
                .count();
    }

    /** All earlier transactions of the same account, not including {@code tx} itself. */
    public List<Transaction> getHistoryBefore(Transaction tx) {
        return transactionsOf(tx.getAccountNumber())
                .filter(t -> !t.getTransactionId().equals(tx.getTransactionId()))
                .filter(t -> !t.getTimestamp().isAfter(tx.getTimestamp()))
                .collect(Collectors.toList());
    }

    /** A read-only copy of the account's recent history, oldest first. */
    public List<Transaction> getHistory(String accountNumber) {
        return List.copyOf(transactionsOf(accountNumber).collect(Collectors.toList()));
    }

    public int getTotalRecorded() {
        return totalRecorded;
    }

    public List<String> getMonitoredAccounts() {
        return new ArrayList<>(historyByAccount.keySet());
    }

    private Stream<Transaction> inWindow(String accountNumber, Duration window, LocalDateTime at) {
        LocalDateTime from = at.minus(window);
        return transactionsOf(accountNumber)
                .filter(t -> !t.getTimestamp().isBefore(from) && !t.getTimestamp().isAfter(at));
    }

    private Stream<Transaction> transactionsOf(String accountNumber) {
        Deque<Transaction> history = historyByAccount.get(accountNumber);
        return history == null ? Stream.empty() : history.stream();
    }
}
