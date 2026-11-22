/***
 * <p>
 * Licensed under MIT License Copyright (c) 2021-2026 Raja Kolli.
 * </p>
 ***/

package com.example.common.idempotency;

import java.time.Duration;
import java.util.Optional;

/** Storage contract for expiring, owner-scoped claims and nullable string results. */
public interface IdempotencyStore {
    enum State {
        ABSENT,
        PROCESSING,
        COMPLETED
    }

    record Snapshot(State state, String result) {
        static final Snapshot ABSENT = new Snapshot(State.ABSENT, null);
        static final Snapshot PROCESSING = new Snapshot(State.PROCESSING, null);
    }

    /** Returns a unique owner token only if there is no live claim or result. */
    Optional<String> acquireLock(String key, Duration ttl);

    /** Returns false if this execution no longer owns the claim. */
    boolean markProcessed(String key, String owner, String result, Duration ttl);

    Snapshot lookup(String key);

    void releaseLock(String key, String owner);
}
