/***
 * <p>
 * Licensed under MIT License Copyright (c) 2021-2026 Raja Kolli.
 * </p>
 ***/

package com.example.common.idempotency;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Relational/JDBC implementation of {@link IdempotencyStore} backed by Spring's {@link
 * JdbcTemplate}. Uses table {@code processed_messages} with unique primary key {@code message_key}
 * to guarantee atomic lock acquisition and processed result persistence across relational
 * databases.
 */
public class JdbcIdempotencyStore implements IdempotencyStore {

    private static final Logger log = LoggerFactory.getLogger(JdbcIdempotencyStore.class);

    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_PROCESSED = "PROCESSED";

    private static final String INSERT_LOCK_SQL =
            "INSERT INTO processed_messages (message_key, status, result, created_at, expires_at) "
                    + "VALUES (?, ?, ?, ?, ?)";

    private static final String UPDATE_EXPIRED_LOCK_SQL =
            "UPDATE processed_messages SET status = ?, created_at = ?, expires_at = ?, result = ? "
                    + "WHERE message_key = ? AND expires_at <= ?";

    private static final String MARK_PROCESSED_SQL =
            "UPDATE processed_messages SET status = ?, result = ?, expires_at = ? "
                    + "WHERE message_key = ? AND status = ? AND result = ? AND expires_at > ?";

    private static final String SELECT_RESULT_SQL =
            "SELECT status, result FROM processed_messages "
                    + "WHERE message_key = ? AND expires_at > ?";

    private static final String RELEASE_LOCK_SQL =
            "DELETE FROM processed_messages WHERE message_key = ? AND status = ? AND result = ?";

    private final JdbcTemplate jdbcTemplate;

    public JdbcIdempotencyStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "JdbcTemplate must not be null");
    }

    @Override
    public Optional<String> acquireLock(String key, Duration ttl) {
        Objects.requireNonNull(key, "Key must not be null");
        Objects.requireNonNull(ttl, "TTL must not be null");

        Instant now = Instant.now();
        Instant expiresAt = now.plus(ttl);

        String owner = UUID.randomUUID().toString();
        try {
            int inserted =
                    jdbcTemplate.update(
                            INSERT_LOCK_SQL,
                            key,
                            STATUS_PROCESSING,
                            owner,
                            Timestamp.from(now),
                            Timestamp.from(expiresAt));
            return inserted > 0 ? Optional.of(owner) : Optional.empty();
        } catch (DuplicateKeyException ex) {
            // Row already exists - check if expired and re-acquire atomically
            int updated =
                    jdbcTemplate.update(
                            UPDATE_EXPIRED_LOCK_SQL,
                            STATUS_PROCESSING,
                            Timestamp.from(now),
                            Timestamp.from(expiresAt),
                            owner,
                            key,
                            Timestamp.from(now));
            return updated > 0 ? Optional.of(owner) : Optional.empty();
        } catch (DataAccessException ex) {
            log.error("Failed to acquire JDBC lock for key '{}'", key, ex);
            return Optional.empty();
        }
    }

    @Override
    public boolean markProcessed(String key, String owner, String result, Duration ttl) {
        Instant now = Instant.now();
        return jdbcTemplate.update(
                        MARK_PROCESSED_SQL,
                        STATUS_PROCESSED,
                        result,
                        Timestamp.from(now.plus(ttl)),
                        key,
                        STATUS_PROCESSING,
                        owner,
                        Timestamp.from(now))
                == 1;
    }

    @Override
    public Snapshot lookup(String key) {
        return jdbcTemplate
                .query(
                        SELECT_RESULT_SQL,
                        (rs, row) ->
                                STATUS_PROCESSING.equals(rs.getString("status"))
                                        ? Snapshot.PROCESSING
                                        : new Snapshot(State.COMPLETED, rs.getString("result")),
                        key,
                        Timestamp.from(Instant.now()))
                .stream()
                .findFirst()
                .orElse(Snapshot.ABSENT);
    }

    @Override
    public void releaseLock(String key, String owner) {
        jdbcTemplate.update(RELEASE_LOCK_SQL, key, STATUS_PROCESSING, owner);
    }
}
