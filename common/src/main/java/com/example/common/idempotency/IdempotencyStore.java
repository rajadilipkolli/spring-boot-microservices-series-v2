/***
 * <p>
 * Licensed under MIT License Copyright (c) 2021-2026 Raja Kolli.
 * </p>
 ***/

package com.example.common.idempotency;

import java.time.Duration;
import java.util.Optional;

/** Storage SPI contract for idempotency locking and result caching. */
public interface IdempotencyStore {

    /**
     * Atomically acquires an execution lock for the given idempotency key.
     *
     * @param key the idempotency key
     * @param ttl time-to-live for the in-flight lock
     * @return true if the lock was acquired, false if already locked or processed
     */
    boolean acquireLock(String key, Duration ttl);

    /**
     * Marks the key as successfully processed, caching the result with the specified TTL.
     *
     * @param key the idempotency key
     * @param result the result of the method execution
     * @param ttl time-to-live for the cached result
     */
    void markProcessed(String key, Object result, Duration ttl);

    /**
     * Retrieves the cached processed result if available.
     *
     * @param key the idempotency key
     * @return Optional containing the cached result, or empty if not found / expired / in-flight
     */
    Optional<Object> getProcessedResult(String key);

    /**
     * Releases an in-flight lock (e.g., if execution failed with an exception).
     *
     * @param key the idempotency key
     */
    void releaseLock(String key);
}
