/***
 * <p>
 * Licensed under MIT License Copyright (c) 2021-2026 Raja Kolli.
 * </p>
 ***/

package com.example.common.idempotency;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory thread-safe implementation of {@link IdempotencyStore} using {@link ConcurrentHashMap}.
 * Suitable for single-instance deployments, tests, and local development.
 */
public class InMemoryIdempotencyStore implements IdempotencyStore {

    private record Entry(String owner, Snapshot snapshot, Instant expiresAt) {
        boolean isExpired(Instant now) {
            return !now.isBefore(expiresAt);
        }
    }

    private final ConcurrentHashMap<String, Entry> store = new ConcurrentHashMap<>();

    @Override
    public Optional<String> acquireLock(String key, Duration ttl) {
        String owner = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Entry claim = new Entry(owner, Snapshot.PROCESSING, now.plus(ttl));
        Entry entry =
                store.compute(key, (k, old) -> old == null || old.isExpired(now) ? claim : old);
        return entry == claim ? Optional.of(owner) : Optional.empty();
    }

    @Override
    public boolean markProcessed(String key, String owner, String result, Duration ttl) {
        Instant now = Instant.now();
        Entry completed = new Entry(null, new Snapshot(State.COMPLETED, result), now.plus(ttl));
        return store.computeIfPresent(
                        key,
                        (k, old) ->
                                owner.equals(old.owner()) && !old.isExpired(now) ? completed : old)
                == completed;
    }

    @Override
    public Snapshot lookup(String key) {
        Entry entry = store.get(key);
        if (entry == null) return Snapshot.ABSENT;
        if (entry.isExpired(Instant.now())) {
            store.remove(key, entry);
            return Snapshot.ABSENT;
        }
        return entry.snapshot();
    }

    @Override
    public void releaseLock(String key, String owner) {
        store.computeIfPresent(key, (k, old) -> owner.equals(old.owner()) ? null : old);
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
