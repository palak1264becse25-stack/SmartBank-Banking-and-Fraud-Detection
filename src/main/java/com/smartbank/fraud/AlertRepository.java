package com.smartbank.fraud;

import java.util.List;
import java.util.Optional;

/**
 * Where fraud alerts are stored.
 *
 * <p>The fraud module only talks to this interface, so storage can be swapped without changing
 * any detection code. {@link InMemoryAlertRepository} is used for now. During integration the
 * backend developer can add a {@code JdbcAlertRepository implements AlertRepository} that uses the
 * tables in {@code sql/fraud_module.sql}, and pass it to the {@link FraudDetection} constructor.
 */
public interface AlertRepository {

    /** Inserts a new alert, or updates it if an alert with the same ID already exists. */
    void save(FraudAlert alert);

    Optional<FraudAlert> findById(String alertId);

    Optional<FraudAlert> findByTransactionId(String transactionId);

    /** All alerts, oldest first. */
    List<FraudAlert> findAll();

    List<FraudAlert> findByStatus(FraudAlert.Status status);
}
