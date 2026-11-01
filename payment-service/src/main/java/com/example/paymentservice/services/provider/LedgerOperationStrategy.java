/*** Licensed under MIT License Copyright (c) 2026 Raja Kolli. ***/
package com.example.paymentservice.services.provider;

import com.example.paymentservice.repositories.CustomerBalanceRepository;
import java.math.BigDecimal;

/** Strategy interface for executing ledger operations against a customer's balance. */
public interface LedgerOperationStrategy {

    /**
     * The operation type handled by this strategy (e.g., "RESERVE", "CAPTURE", "RELEASE").
     *
     * @return operation type string
     */
    String getOperationType();

    /**
     * Executes the balance mutation for the given customer and amount.
     *
     * @param customerRepository the balance repository to perform mutation
     * @param customerId the customer identifier
     * @param amount the amount to mutate
     * @return true if the balance mutation succeeded, false otherwise
     */
    boolean execute(
            CustomerBalanceRepository customerRepository, Long customerId, BigDecimal amount);
}
