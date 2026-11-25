package com.smartbank.fraud;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * The fraud detection engine and the single entry point for other modules.
 *
 * <p>For every transaction it: records it in the {@link TransactionMonitor}, checks it against
 * the {@link FraudRules}, and if any rule is broken creates a {@link FraudAlert}, stores it in the
 * {@link AlertRepository} and notifies any registered listeners.
 *
 * <p><b>Integration:</b>
 * <ul>
 *   <li>Banking Operations: after a deposit, withdrawal or transfer succeeds, call
 *       {@code fraudDetection.analyze(transaction)}.</li>
 *   <li>Backend: pass a JDBC-backed {@link AlertRepository} to the constructor to save alerts in
 *       the database instead of memory.</li>
 *   <li>Notifications: use {@link #addAlertListener(Consumer)} to react to new alerts, for example
 *       to show a warning or send an email.</li>
 * </ul>
 */
public class FraudDetection {

    private final FraudRules rules;
    private final TransactionMonitor monitor;
    private final AlertRepository repository;
    private final Map<String, Transaction> analysedTransactions = new LinkedHashMap<>();
    private final List<Consumer<FraudAlert>> alertListeners = new CopyOnWriteArrayList<>();
    private int alertSequence;

    /** Creates an engine with the default rules and in-memory storage. */
    public FraudDetection() {
        this(new FraudRules(), new TransactionMonitor(), new InMemoryAlertRepository());
    }

    public FraudDetection(FraudRules rules, TransactionMonitor monitor, AlertRepository repository) {
        this.rules = Objects.requireNonNull(rules, "rules");
        this.monitor = Objects.requireNonNull(monitor, "monitor");
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    /**
     * Checks one transaction for fraud.
     *
     * @return the alert that was raised, or an empty Optional if the transaction looks normal
     * @throws IllegalArgumentException if the same transaction ID has already been analysed
     */
    public synchronized Optional<FraudAlert> analyze(Transaction tx) {
        Objects.requireNonNull(tx, "transaction");
        if (analysedTransactions.containsKey(tx.getTransactionId())) {
            throw new IllegalArgumentException("Transaction " + tx.getTransactionId() + " has already been analysed");
        }

        monitor.record(tx);
        analysedTransactions.put(tx.getTransactionId(), tx);

        List<FraudRules.Violation> violations = rules.evaluate(tx, monitor);
        if (violations.isEmpty()) {
            return Optional.empty();
        }

        FraudAlert alert = FraudAlert.fromViolations(nextAlertId(), tx, violations, LocalDateTime.now());
        repository.save(alert);
        notifyListeners(alert);
        return Optional.of(alert);
    }

    /** Checks several transactions in time order and returns the alerts that were raised. */
    public List<FraudAlert> analyzeAll(Collection<Transaction> transactions) {
        List<Transaction> sorted = new ArrayList<>(transactions);
        sorted.sort(Comparator.comparing(Transaction::getTimestamp));
        List<FraudAlert> alerts = new ArrayList<>();
        for (Transaction tx : sorted) {
            analyze(tx).ifPresent(alerts::add);
        }
        return alerts;
    }

    // ---------------------------------- Alert management (Task 6) ----------------------------------

    public synchronized FraudAlert markReviewed(String alertId, String reviewer, String note) {
        FraudAlert alert = requireAlert(alertId);
        alert.markReviewed(reviewer, note);
        repository.save(alert);
        return alert;
    }

    public synchronized FraudAlert confirmFraud(String alertId, String reviewer, String note) {
        FraudAlert alert = requireAlert(alertId);
        alert.confirmFraud(reviewer, note);
        repository.save(alert);
        return alert;
    }

    /** Open alerts that still need review. */
    public List<FraudAlert> getFlaggedAlerts() {
        return repository.findByStatus(FraudAlert.Status.FLAGGED);
    }

    /** Every alert ever raised, including reviewed ones (the alert history). */
    public List<FraudAlert> getAllAlerts() {
        return repository.findAll();
    }

    /** Finds an alert by its alert ID (FA-0001) or by its transaction ID (TX101). Not case-sensitive. */
    public Optional<FraudAlert> findAlert(String alertOrTransactionId) {
        if (alertOrTransactionId == null || alertOrTransactionId.isBlank()) {
            return Optional.empty();
        }
        String id = alertOrTransactionId.trim();
        for (String candidate : List.of(id, id.toUpperCase(Locale.ROOT))) {
            Optional<FraudAlert> alert = repository.findById(candidate)
                    .or(() -> repository.findByTransactionId(candidate));
            if (alert.isPresent()) {
                return alert;
            }
        }
        return Optional.empty();
    }

    public Optional<FraudAlert> findAlertForTransaction(String transactionId) {
        return repository.findByTransactionId(transactionId);
    }

    public synchronized Optional<Transaction> findTransaction(String transactionId) {
        if (transactionId == null) {
            return Optional.empty();
        }
        String id = transactionId.trim();
        Transaction tx = analysedTransactions.get(id);
        return Optional.ofNullable(tx != null ? tx : analysedTransactions.get(id.toUpperCase(Locale.ROOT)));
    }

    /** All transactions checked so far, in the order they were analysed. */
    public synchronized List<Transaction> getAnalysedTransactions() {
        return new ArrayList<>(analysedTransactions.values());
    }

    public void addAlertListener(Consumer<FraudAlert> listener) {
        alertListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public FraudRules getRules() {
        return rules;
    }

    public TransactionMonitor getMonitor() {
        return monitor;
    }

    // ---------------------------------- Helpers ----------------------------------

    private FraudAlert requireAlert(String alertId) {
        return findAlert(alertId)
                .orElseThrow(() -> new IllegalArgumentException("No alert found with ID " + alertId));
    }

    private String nextAlertId() {
        String id;
        do {
            id = String.format("FA-%04d", ++alertSequence);
        } while (repository.findById(id).isPresent()); // skip IDs already used, e.g. alerts loaded from a database
        return id;
    }

    private void notifyListeners(FraudAlert alert) {
        for (Consumer<FraudAlert> listener : alertListeners) {
            try {
                listener.accept(alert);
            } catch (RuntimeException e) {
                // A broken listener must never stop fraud detection.
                System.err.println("Fraud alert listener failed: " + e.getMessage());
            }
        }
    }
}
