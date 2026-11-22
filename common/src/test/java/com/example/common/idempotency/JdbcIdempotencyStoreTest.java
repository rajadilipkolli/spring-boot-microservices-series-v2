/***
 * <p>
 * Licensed under MIT License Copyright (c) 2026 Raja Kolli.
 * </p>
 ***/

package com.example.common.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.postgresql.PostgreSQLContainer;

class JdbcIdempotencyStoreTest {

    private static PostgreSQLContainer postgres;
    private static JdbcTemplate jdbcTemplate;
    private JdbcIdempotencyStore store;

    @BeforeAll
    static void startContainer() {
        postgres =
                new PostgreSQLContainer("postgres:19-alpine")
                        .withDatabaseName("testdb")
                        .withUsername("test")
                        .withPassword("test");
        postgres.start();

        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.postgresql.Driver");
        ds.setUrl(postgres.getJdbcUrl());
        ds.setUsername(postgres.getUsername());
        ds.setPassword(postgres.getPassword());

        jdbcTemplate = new JdbcTemplate(ds);
        ResourceDatabasePopulator populator =
                new ResourceDatabasePopulator(
                        new ClassPathResource("schema-processed-messages.sql"));
        populator.execute(ds);
    }

    @AfterAll
    static void stopContainer() {
        if (postgres != null) {
            postgres.stop();
        }
    }

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM processed_messages");
        store = new JdbcIdempotencyStore(jdbcTemplate);
    }

    @Test
    void shouldAcquireLockSuccessfully() {
        boolean acquired = store.acquireLock("order-100", Duration.ofSeconds(30));
        assertThat(acquired).isTrue();

        // Second attempt on active lock should fail
        boolean secondAttempt = store.acquireLock("order-100", Duration.ofSeconds(30));
        assertThat(secondAttempt).isFalse();
    }

    @Test
    void shouldReacquireLockWhenPreviousLockExpired() throws InterruptedException {
        boolean acquired = store.acquireLock("order-expired", Duration.ofMillis(50));
        assertThat(acquired).isTrue();

        Thread.sleep(80);

        // Previous lock expired, should be re-acquired via atomic update
        boolean reacquired = store.acquireLock("order-expired", Duration.ofSeconds(30));
        assertThat(reacquired).isTrue();
    }

    @Test
    void shouldMarkProcessedAndRetrieveResult() {
        store.acquireLock("order-200", Duration.ofSeconds(30));
        store.markProcessed("order-200", "ORDER_CREATED_RESULT", Duration.ofSeconds(30));

        Optional<Object> result = store.getProcessedResult("order-200");
        assertThat(result).isPresent().contains("ORDER_CREATED_RESULT");

        // Lock should not be re-acquirable once processed
        boolean lockAcquired = store.acquireLock("order-200", Duration.ofSeconds(30));
        assertThat(lockAcquired).isFalse();
    }

    @Test
    void shouldReturnEmptyWhenProcessedResultExpired() throws InterruptedException {
        store.acquireLock("order-exp-result", Duration.ofSeconds(30));
        store.markProcessed("order-exp-result", "TEMP_RESULT", Duration.ofMillis(50));

        assertThat(store.getProcessedResult("order-exp-result"))
                .isPresent()
                .contains("TEMP_RESULT");

        Thread.sleep(80);

        assertThat(store.getProcessedResult("order-exp-result")).isEmpty();

        // Can re-acquire once expired
        boolean reacquired = store.acquireLock("order-exp-result", Duration.ofSeconds(30));
        assertThat(reacquired).isTrue();
    }

    @Test
    void shouldReleaseLockWhenProcessing() {
        boolean acquired = store.acquireLock("order-cancel", Duration.ofSeconds(30));
        assertThat(acquired).isTrue();

        store.releaseLock("order-cancel");

        // Should be able to re-acquire after release
        boolean reacquired = store.acquireLock("order-cancel", Duration.ofSeconds(30));
        assertThat(reacquired).isTrue();
    }

    @Test
    void shouldNotReleaseLockIfAlreadyProcessed() {
        store.acquireLock("order-safe", Duration.ofSeconds(30));
        store.markProcessed("order-safe", "DONE", Duration.ofSeconds(30));

        // Release lock targets PROCESSING status only
        store.releaseLock("order-safe");

        // Result should still be intact
        Optional<Object> result = store.getProcessedResult("order-safe");
        assertThat(result).isPresent().contains("DONE");
    }

    @Test
    void shouldReturnEmptyWhenResultNotFound() {
        Optional<Object> result = store.getProcessedResult("non-existent-key");
        assertThat(result).isEmpty();
    }
}
