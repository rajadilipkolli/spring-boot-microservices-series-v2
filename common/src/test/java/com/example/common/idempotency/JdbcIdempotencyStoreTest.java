/***
 * <p>
 * Licensed under MIT License Copyright (c) 2026 Raja Kolli.
 * </p>
 ***/

package com.example.common.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.common.idempotency.IdempotencyStore.Snapshot;
import com.example.common.idempotency.IdempotencyStore.State;
import java.time.Duration;
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
        assertThat(store.acquireLock("order-100", Duration.ofSeconds(30))).isPresent();
        assertThat(store.acquireLock("order-100", Duration.ofSeconds(30))).isEmpty();
    }

    @Test
    void shouldReacquireLockWhenPreviousLockExpired() throws InterruptedException {
        assertThat(store.acquireLock("order-expired", Duration.ofMillis(50))).isPresent();

        Thread.sleep(80);

        assertThat(store.acquireLock("order-expired", Duration.ofSeconds(30))).isPresent();
    }

    @Test
    void shouldMarkProcessedAndRetrieveResult() {
        Duration ttl = Duration.ofSeconds(30);
        String owner = store.acquireLock("order-200", ttl).orElseThrow();
        assertThat(store.markProcessed("order-200", owner, "ORDER_CREATED_RESULT", ttl)).isTrue();

        assertThat(store.lookup("order-200"))
                .isEqualTo(new Snapshot(State.COMPLETED, "ORDER_CREATED_RESULT"));
        assertThat(store.acquireLock("order-200", ttl)).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenProcessedResultExpired() throws InterruptedException {
        Duration ttl = Duration.ofSeconds(30);
        String owner = store.acquireLock("order-exp-result", ttl).orElseThrow();
        assertThat(
                        store.markProcessed(
                                "order-exp-result", owner, "TEMP_RESULT", Duration.ofMillis(50)))
                .isTrue();

        assertThat(store.lookup("order-exp-result"))
                .isEqualTo(new Snapshot(State.COMPLETED, "TEMP_RESULT"));

        Thread.sleep(80);

        assertThat(store.lookup("order-exp-result")).isEqualTo(Snapshot.ABSENT);
        assertThat(store.acquireLock("order-exp-result", ttl)).isPresent();
    }

    @Test
    void shouldReleaseLockWhenProcessing() {
        Duration ttl = Duration.ofSeconds(30);
        String owner = store.acquireLock("order-cancel", ttl).orElseThrow();

        store.releaseLock("order-cancel", owner);

        assertThat(store.acquireLock("order-cancel", ttl)).isPresent();
    }

    @Test
    void shouldNotReleaseLockIfAlreadyProcessed() {
        Duration ttl = Duration.ofSeconds(30);
        String owner = store.acquireLock("order-safe", ttl).orElseThrow();
        assertThat(store.markProcessed("order-safe", owner, "DONE", ttl)).isTrue();

        store.releaseLock("order-safe", owner);

        assertThat(store.lookup("order-safe")).isEqualTo(new Snapshot(State.COMPLETED, "DONE"));
    }

    @Test
    void shouldReturnEmptyWhenResultNotFound() {
        assertThat(store.lookup("non-existent-key")).isEqualTo(Snapshot.ABSENT);
    }
}
