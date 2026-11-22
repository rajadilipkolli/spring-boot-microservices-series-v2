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
import java.util.Optional;
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
        boolean acquired = store.acquireLock("key-1", Duration.ofSeconds(10));
        assertThat(acquired).isTrue();

        // Second attempt should fail
        boolean secondAttempt = store.acquireLock("key-1", Duration.ofSeconds(10));
        assertThat(secondAttempt).isFalse();
    }

    @Test
    void shouldReleaseLockAndAllowReacquire() {
        boolean acquired = store.acquireLock("key-1", Duration.ofSeconds(10));
        assertThat(acquired).isTrue();

        store.releaseLock("key-1");

        boolean reacquired = store.acquireLock("key-1", Duration.ofSeconds(10));
        assertThat(reacquired).isTrue();
    }

    @Test
    void shouldMarkProcessedAndReturnResult() {
        store.acquireLock("key-1", Duration.ofSeconds(10));
        store.markProcessed("key-1", "RESULT_OK", Duration.ofSeconds(10));

        Optional<Object> result = store.getProcessedResult("key-1");
        assertThat(result).isPresent().contains("RESULT_OK");

        // Lock should not be re-acquirable once processed
        boolean lockAcquired = store.acquireLock("key-1", Duration.ofSeconds(10));
        assertThat(lockAcquired).isFalse();

        // Releasing lock on processed entry should not remove the processed state
        store.releaseLock("key-1");
        assertThat(store.getProcessedResult("key-1")).isPresent().contains("RESULT_OK");
    }

    @Test
    void shouldHandleNullProcessedResult() {
        store.acquireLock("key-null", Duration.ofSeconds(10));
        store.markProcessed("key-null", null, Duration.ofSeconds(10));

        // When processed with null, Optional is empty (representing null return)
        Optional<Object> result = store.getProcessedResult("key-null");
        assertThat(result).isEmpty();

        // But lock cannot be reacquired because state is PROCESSED and not expired
        boolean lockAcquired = store.acquireLock("key-null", Duration.ofSeconds(10));
        assertThat(lockAcquired).isFalse();
    }

    @Test
    void shouldExpireLockAfterTtl() throws InterruptedException {
        store.acquireLock("key-expire", Duration.ofMillis(50));
        assertThat(store.acquireLock("key-expire", Duration.ofMillis(50))).isFalse();

        Thread.sleep(80);

        // After TTL expired, acquiring lock should succeed
        boolean acquiredAfterExpiry = store.acquireLock("key-expire", Duration.ofSeconds(10));
        assertThat(acquiredAfterExpiry).isTrue();
    }

    @Test
    void shouldExpireProcessedResultAfterTtl() throws InterruptedException {
        store.acquireLock("key-proc-expire", Duration.ofSeconds(10));
        store.markProcessed("key-proc-expire", "EXPIRED_DATA", Duration.ofMillis(50));

        assertThat(store.getProcessedResult("key-proc-expire"))
                .isPresent()
                .contains("EXPIRED_DATA");

        Thread.sleep(80);

        assertThat(store.getProcessedResult("key-proc-expire")).isEmpty();
        // After expiry, should be able to acquire lock again
        assertThat(store.acquireLock("key-proc-expire", Duration.ofSeconds(10))).isTrue();
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
                boolean acquired = store.acquireLock("concurrent-key", Duration.ofSeconds(10));
                if (acquired) {
                    successCount.incrementAndGet();
                }
                return acquired;
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
        assertThatThrownBy(() -> store.markProcessed(null, "val", Duration.ofSeconds(1)))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> store.markProcessed("k", "val", null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> store.getProcessedResult(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> store.releaseLock(null)).isInstanceOf(NullPointerException.class);
    }
}
