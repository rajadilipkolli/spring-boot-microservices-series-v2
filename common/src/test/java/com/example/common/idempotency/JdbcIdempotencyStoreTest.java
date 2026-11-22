/***
 * <p>
 * Licensed under MIT License Copyright (c) 2021-2026 Raja Kolli.
 * </p>
 ***/

package com.example.common.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

class JdbcIdempotencyStoreTest {

    private JdbcTemplate jdbcTemplate;
    private JdbcIdempotencyStore store;

    @BeforeEach
    void setUp() {
        DataSource dataSource =
                new EmbeddedDatabaseBuilder()
                        .setType(EmbeddedDatabaseType.H2)
                        .addScript("classpath:schema-processed-messages.sql")
                        .build();

        jdbcTemplate = new JdbcTemplate(dataSource);
        store = new JdbcIdempotencyStore(jdbcTemplate);
    }

    @Test
    void shouldAcquireLockSuccessfully() {
        var acquired = store.acquireLock("order-100", Duration.ofSeconds(30));
        assertThat(acquired).isPresent();

        // Second attempt on active lock should fail
        var secondAttempt = store.acquireLock("order-100", Duration.ofSeconds(30));
        assertThat(secondAttempt).isEmpty();
    }

    @Test
    void shouldReacquireLockWhenPreviousLockExpired() throws InterruptedException {
        var acquired = store.acquireLock("order-expired", Duration.ofMillis(50));
        assertThat(acquired).isPresent();

        Thread.sleep(80);

        // Previous lock expired, should be re-acquired via atomic update
        var reacquired = store.acquireLock("order-expired", Duration.ofSeconds(30));
        assertThat(reacquired).isPresent();
    }

    @Test
    void shouldMarkProcessedAndRetrieveResult() {
        String owner = store.acquireLock("order-200", Duration.ofSeconds(30)).orElseThrow();
        store.markProcessed("order-200", owner, "ORDER_CREATED_RESULT", Duration.ofSeconds(30));

        var result = store.lookup("order-200");
        assertThat(result.result()).isEqualTo("ORDER_CREATED_RESULT");

        // Lock should not be re-acquirable once processed
        var lockAcquired = store.acquireLock("order-200", Duration.ofSeconds(30));
        assertThat(lockAcquired).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenProcessedResultExpired() throws InterruptedException {
        String owner = store.acquireLock("order-exp-result", Duration.ofSeconds(30)).orElseThrow();
        store.markProcessed("order-exp-result", owner, "TEMP_RESULT", Duration.ofMillis(50));

        assertThat(store.lookup("order-exp-result").result()).isEqualTo("TEMP_RESULT");

        Thread.sleep(80);

        assertThat(store.lookup("order-exp-result")).isEqualTo(IdempotencyStore.Snapshot.ABSENT);

        // Can re-acquire once expired
        var reacquired = store.acquireLock("order-exp-result", Duration.ofSeconds(30));
        assertThat(reacquired).isPresent();
    }

    @Test
    void shouldReleaseLockWhenProcessing() {
        var acquired = store.acquireLock("order-cancel", Duration.ofSeconds(30));
        assertThat(acquired).isPresent();

        store.releaseLock("order-cancel", acquired.orElseThrow());

        // Should be able to re-acquire after release
        var reacquired = store.acquireLock("order-cancel", Duration.ofSeconds(30));
        assertThat(reacquired).isPresent();
    }

    @Test
    void shouldNotReleaseLockIfAlreadyProcessed() {
        String owner = store.acquireLock("order-safe", Duration.ofSeconds(30)).orElseThrow();
        store.markProcessed("order-safe", owner, "DONE", Duration.ofSeconds(30));

        // Release lock targets PROCESSING status only
        store.releaseLock("order-safe", owner);

        // Result should still be intact
        var result = store.lookup("order-safe");
        assertThat(result.result()).isEqualTo("DONE");
    }

    @Test
    void shouldReturnEmptyWhenResultNotFound() {
        var result = store.lookup("non-existent-key");
        assertThat(result).isEqualTo(IdempotencyStore.Snapshot.ABSENT);
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
