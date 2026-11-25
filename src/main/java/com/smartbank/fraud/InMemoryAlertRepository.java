package com.smartbank.fraud;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/** Keeps alerts in memory. Good for development and demos; data is lost when the program stops. */
public class InMemoryAlertRepository implements AlertRepository {

    private final Map<String, FraudAlert> alertsById = new LinkedHashMap<>();
    private final Map<String, FraudAlert> alertsByTransaction = new LinkedHashMap<>();

    @Override
    public synchronized void save(FraudAlert alert) {
        Objects.requireNonNull(alert, "alert");
        alertsById.put(alert.getAlertId(), alert);
        alertsByTransaction.put(alert.getTransactionId(), alert);
    }

    @Override
    public synchronized Optional<FraudAlert> findById(String alertId) {
        return Optional.ofNullable(alertsById.get(alertId));
    }

    @Override
    public synchronized Optional<FraudAlert> findByTransactionId(String transactionId) {
        return Optional.ofNullable(alertsByTransaction.get(transactionId));
    }

    @Override
    public synchronized List<FraudAlert> findAll() {
        return new ArrayList<>(alertsById.values());
    }

    @Override
    public synchronized List<FraudAlert> findByStatus(FraudAlert.Status status) {
        return alertsById.values().stream()
                .filter(a -> a.getStatus() == status)
                .collect(Collectors.toList());
    }
}
