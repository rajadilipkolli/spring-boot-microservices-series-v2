/***
 * <p>
 * Licensed under MIT License Copyright (c) 2021-2026 Raja Kolli.
 * </p>
 ***/

package com.example.common.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InMemoryIdempotencyStoreTest {

    private InMemoryIdempotencyStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryIdempotencyStore();
    }

    @Test
    void shouldAcquireLockSuccessfully() {
        var acquired = store.acquireLock("key-1", Duration.ofSeconds(10));
        assertThat(acquired).isPresent();

        // Second attempt should fail
        var secondAttempt = store.acquireLock("key-1", Duration.ofSeconds(10));
        assertThat(secondAttempt).isEmpty();
    }

    @Test
    void shouldReleaseLockAndAllowReacquire() {
        var acquired = store.acquireLock("key-1", Duration.ofSeconds(10));
        assertThat(acquired).isPresent();

        store.releaseLock("key-1", acquired.orElseThrow());

        var reacquired = store.acquireLock("key-1", Duration.ofSeconds(10));
        assertThat(reacquired).isPresent();
    }

    @Test
    void shouldMarkProcessedAndReturnResult() {
        String owner = store.acquireLock("key-1", Duration.ofSeconds(10)).orElseThrow();
        store.markProcessed("key-1", owner, "RESULT_OK", Duration.ofSeconds(10));

        var result = store.lookup("key-1");
        assertThat(result.result()).isEqualTo("RESULT_OK");

        // Lock should not be re-acquirable once processed
        var lockAcquired = store.acquireLock("key-1", Duration.ofSeconds(10));
        assertThat(lockAcquired).isEmpty();

        // Releasing lock on processed entry should not remove the processed state
        store.releaseLock("key-1", owner);
        assertThat(store.lookup("key-1").result()).isEqualTo("RESULT_OK");
    }

    @Test
    void shouldHandleNullProcessedResult() {
        String owner = store.acquireLock("key-null", Duration.ofSeconds(10)).orElseThrow();
        store.markProcessed("key-null", owner, null, Duration.ofSeconds(10));

        // Completion is independent of the nullable value.
        var result = store.lookup("key-null");
        assertThat(result)
                .isEqualTo(new IdempotencyStore.Snapshot(IdempotencyStore.State.COMPLETED, null));

        // But lock cannot be reacquired because state is PROCESSED and not expired
        var lockAcquired = store.acquireLock("key-null", Duration.ofSeconds(10));
        assertThat(lockAcquired).isEmpty();
    }

    @Test
    void shouldExpireLockAfterTtl() throws InterruptedException {
        store.acquireLock("key-expire", Duration.ofMillis(50));
        assertThat(store.acquireLock("key-expire", Duration.ofMillis(50))).isEmpty();

        Thread.sleep(80);

        // After TTL expired, acquiring lock should succeed
        var acquiredAfterExpiry = store.acquireLock("key-expire", Duration.ofSeconds(10));
        assertThat(acquiredAfterExpiry).isPresent();
    }

    @Test
    void shouldExpireProcessedResultAfterTtl() throws InterruptedException {
        String owner = store.acquireLock("key-proc-expire", Duration.ofSeconds(10)).orElseThrow();
        store.markProcessed("key-proc-expire", owner, "EXPIRED_DATA", Duration.ofMillis(50));

        assertThat(store.lookup("key-proc-expire").result()).isEqualTo("EXPIRED_DATA");

        Thread.sleep(80);

        assertThat(store.lookup("key-proc-expire")).isEqualTo(IdempotencyStore.Snapshot.ABSENT);
        // After expiry, should be able to acquire lock again
        assertThat(store.acquireLock("key-proc-expire", Duration.ofSeconds(10))).isPresent();
    }

    @Test
    void shouldEvictExpiredAndClear() throws InterruptedException {
        store.acquireLock("k1", Duration.ofMillis(50));
        store.acquireLock("k2", Duration.ofSeconds(10));

        Thread.sleep(80);

        store.evictExpired();
        assertThat(store.size()).isEqualTo(1);

        store.clear();
        assertThat(store.size()).isEqualTo(0);
    }

    @Test
    void shouldHandleConcurrentLockAcquisition() throws InterruptedException {
        int threads = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);

        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            tasks.add(() -> {
                latch.await();
                var acquired = store.acquireLock("concurrent-key", Duration.ofSeconds(10));
                if (acquired.isPresent()) {
                    successCount.incrementAndGet();
                }
                return acquired.isPresent();
            });
        }

        // Trigger all tasks concurrently
        latch.countDown();
        List<Future<Boolean>> futures = executor.invokeAll(tasks);
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(1);
    }

    @Test
    void shouldValidateArguments() {
        assertThatThrownBy(() -> store.acquireLock(null, Duration.ofSeconds(1)))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> store.acquireLock("k", null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> store.markProcessed(null, "owner", "val", Duration.ofSeconds(1)))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> store.markProcessed("k", "owner", "val", null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> store.lookup(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> store.releaseLock(null, "owner"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void staleOwnerCannotChangeReplacementClaim() {
        String key = "replace";
        assertThat(store.lookup(key)).isEqualTo(IdempotencyStore.Snapshot.ABSENT);
        String stale = store.acquireLock(key, Duration.ZERO).orElseThrow();
        Duration ttl = Duration.ofMinutes(1);
        String current = store.acquireLock(key, ttl).orElseThrow();
        store.releaseLock(key, stale);
        assertThat(store.markProcessed(key, stale, "stale", ttl)).isFalse();
        assertThat(store.lookup(key)).isEqualTo(IdempotencyStore.Snapshot.PROCESSING);
        assertThat(store.markProcessed(key, current, null, ttl)).isTrue();
        assertThat(store.lookup(key))
                .isEqualTo(new IdempotencyStore.Snapshot(IdempotencyStore.State.COMPLETED, null));
    }
}
