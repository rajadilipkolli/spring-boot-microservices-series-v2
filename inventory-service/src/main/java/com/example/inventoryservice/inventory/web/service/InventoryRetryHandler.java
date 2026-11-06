/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.inventoryservice.inventory.web.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

/**
 * Executes persistence operations with custom fallback and retry handling for concurrent
 * modifications, optimistic locking failures, and saving issues.
 */
@Component
class InventoryRetryHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(InventoryRetryHandler.class);
    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final long INITIAL_BACKOFF_MS = 50;

    /**
     * Executes the given operation with up to {@code maxRetries} attempts if an {@link
     * OptimisticLockingFailureException} occurs.
     *
     * @param operation the update operation to execute
     * @param <T> the return type
     * @return the result of the operation
     */
    public <T> T executeWithRetry(RetryCallback<T> operation) {
        return executeWithRetry(operation, DEFAULT_MAX_RETRIES);
    }

    /**
     * Executes the given operation with up to the specified max attempts.
     *
     * @param operation the update operation to execute
     * @param maxAttempts maximum attempts
     * @param <T> the return type
     * @return the result of the operation
     */
    public <T> T executeWithRetry(RetryCallback<T> operation, int maxAttempts) {
        int attempt = 0;
        long backoff = INITIAL_BACKOFF_MS;
        while (true) {
            attempt++;
            try {
                return operation.doInRetry();
            } catch (OptimisticLockingFailureException ex) {
                if (attempt >= maxAttempts) {
                    LOGGER.error(
                            "Persistence retry exhausted after {} attempts. Propagating failure.",
                            attempt,
                            ex);
                    throw ex;
                }
                LOGGER.warn(
                        "Optimistic lock conflict encountered on attempt {}. Retrying in {} ms...",
                        attempt,
                        backoff);
                try {
                    Thread.sleep(backoff);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw ex;
                }
                backoff *= 2;
            }
        }
    }

    @FunctionalInterface
    public interface RetryCallback<T> {
        T doInRetry() throws OptimisticLockingFailureException;
    }
}
