/***
 * <p>
 * Licensed under MIT License Copyright (c) 2021-2026 Raja Kolli.
 * </p>
 ***/

package com.example.common.idempotency;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory thread-safe implementation of {@link IdempotencyStore} using {@link ConcurrentHashMap}.
 * Suitable for single-instance deployments, tests, and local development.
 */
public class InMemoryIdempotencyStore implements IdempotencyStore {

    private enum State {
        IN_FLIGHT,
        PROCESSED
    }

    private static final Object NULL_SENTINEL = new Object();

    private record Entry(State state, Object result, Instant expiresAt) {
        boolean isExpired(Instant now) {
            return now.isAfter(expiresAt);
        }
    }

    private final ConcurrentHashMap<String, Entry> store = new ConcurrentHashMap<>();

    @Override
    public boolean acquireLock(String key, Duration ttl) {
        Objects.requireNonNull(key, "Key must not be null");
        Objects.requireNonNull(ttl, "TTL must not be null");

        Instant now = Instant.now();
        Instant expiresAt = now.plus(ttl);

        boolean[] acquired = new boolean[1];

        store.compute(
                key,
                (k, existing) -> {
                    if (existing == null || existing.isExpired(now)) {
                        acquired[0] = true;
                        return new Entry(State.IN_FLIGHT, null, expiresAt);
                    }
                    acquired[0] = false;
                    return existing;
                });

        return acquired[0];
    }

    @Override
    public void markProcessed(String key, Object result, Duration ttl) {
        Objects.requireNonNull(key, "Key must not be null");
        Objects.requireNonNull(ttl, "TTL must not be null");

        Instant now = Instant.now();
        Instant expiresAt = now.plus(ttl);
        Object storedResult = result == null ? NULL_SENTINEL : result;

        store.put(key, new Entry(State.PROCESSED, storedResult, expiresAt));
    }

    @Override
    public Optional<Object> getProcessedResult(String key) {
        Objects.requireNonNull(key, "Key must not be null");

        Instant now = Instant.now();
        Entry entry = store.get(key);

        if (entry == null) {
            return Optional.empty();
        }

        if (entry.isExpired(now)) {
            store.remove(key, entry);
            return Optional.empty();
        }

        if (entry.state() == State.PROCESSED) {
            Object res = entry.result();
            return Optional.ofNullable(res == NULL_SENTINEL ? null : res);
        }

        return Optional.empty();
    }

    @Override
    public void releaseLock(String key) {
        Objects.requireNonNull(key, "Key must not be null");

        store.computeIfPresent(
                key,
                (k, existing) -> {
                    if (existing.state() == State.IN_FLIGHT) {
                        return null;
                    }
                    return existing;
                });
    }

    /** Evicts all expired entries from the store. */
    public void evictExpired() {
        Instant now = Instant.now();
        store.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
    }

    /** Clears all entries from the store. */
    public void clear() {
        store.clear();
    }

    /** Returns current size of store (including potentially un-evicted entries). */
    public int size() {
        return store.size();
    }
}
