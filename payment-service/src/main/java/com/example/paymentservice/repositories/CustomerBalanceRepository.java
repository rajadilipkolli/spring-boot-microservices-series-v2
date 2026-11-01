/*** Licensed under MIT License Copyright (c) 2026 Raja Kolli. ***/
package com.example.paymentservice.repositories;

import java.math.BigDecimal;

public interface CustomerBalanceRepository {

    /**
     * Atomically transfers funds from available to reserved balance if available balance is
     * sufficient.
     *
     * @param customerId the customer identifier
     * @param amount the amount to reserve
     * @return true if funds were reserved, false if funds are insufficient or the customer is
     *     absent or deleted
     */
    boolean reserveFunds(Long customerId, BigDecimal amount);

    /**
     * Atomically deducts funds from reserved balance upon order confirmation.
     *
     * @param customerId the customer identifier
     * @param amount the amount to capture
     * @return true if funds were captured, false if insufficient reserved balance or customer not
     *     found
     */
    boolean captureFunds(Long customerId, BigDecimal amount);

    /**
     * Atomically releases funds from reserved back to available balance upon order rollback.
     *
     * @param customerId the customer identifier
     * @param amount the amount to release
     * @return true if funds were released, false if insufficient reserved balance or customer not
     *     found
     */
    boolean releaseFunds(Long customerId, BigDecimal amount);
}
