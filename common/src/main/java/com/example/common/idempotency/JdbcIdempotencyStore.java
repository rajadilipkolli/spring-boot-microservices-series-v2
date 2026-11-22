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
                    + "VALUES (?, ?, NULL, ?, ?)";

    private static final String UPDATE_EXPIRED_LOCK_SQL =
            "UPDATE processed_messages SET status = ?, created_at = ?, expires_at = ?, result = NULL "
                    + "WHERE message_key = ? AND expires_at < ?";

    private static final String MARK_PROCESSED_SQL =
            "UPDATE processed_messages SET status = ?, result = ?, expires_at = ? "
                    + "WHERE message_key = ?";

    private static final String SELECT_RESULT_SQL =
            "SELECT result, expires_at FROM processed_messages "
                    + "WHERE message_key = ? AND status = ?";

    private static final String RELEASE_LOCK_SQL =
            "DELETE FROM processed_messages WHERE message_key = ? AND status = ?";

    private final JdbcTemplate jdbcTemplate;

    public JdbcIdempotencyStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "JdbcTemplate must not be null");
    }

    @Override
    public boolean acquireLock(String key, Duration ttl) {
        Objects.requireNonNull(key, "Key must not be null");
        Objects.requireNonNull(ttl, "TTL must not be null");

        Instant now = Instant.now();
        Instant expiresAt = now.plus(ttl);

        try {
            int inserted =
                    jdbcTemplate.update(
                            INSERT_LOCK_SQL,
                            key,
                            STATUS_PROCESSING,
                            Timestamp.from(now),
                            Timestamp.from(expiresAt));
            return inserted > 0;
        } catch (DuplicateKeyException ex) {
            // Row already exists - check if expired and re-acquire atomically
            int updated =
                    jdbcTemplate.update(
                            UPDATE_EXPIRED_LOCK_SQL,
                            STATUS_PROCESSING,
                            Timestamp.from(now),
                            Timestamp.from(expiresAt),
                            key,
                            Timestamp.from(now));
            return updated > 0;
        } catch (DataAccessException ex) {
            log.error("Failed to acquire JDBC lock for key '{}'", key, ex);
            return false;
        }
    }

    @Override
    public void markProcessed(String key, Object result, Duration ttl) {
        Objects.requireNonNull(key, "Key must not be null");
        Objects.requireNonNull(ttl, "TTL must not be null");

        Instant expiresAt = Instant.now().plus(ttl);
        String stringResult = result == null ? null : result.toString();

        jdbcTemplate.update(
                MARK_PROCESSED_SQL, STATUS_PROCESSED, stringResult, Timestamp.from(expiresAt), key);
    }

    @Override
    public Optional<Object> getProcessedResult(String key) {
        Objects.requireNonNull(key, "Key must not be null");

        Instant now = Instant.now();

        return jdbcTemplate
                .query(
                        SELECT_RESULT_SQL,
                        (rs, rowNum) -> {
                            Timestamp expiresAtTs = rs.getTimestamp("expires_at");
                            Instant expiresAt =
                                    expiresAtTs != null ? expiresAtTs.toInstant() : Instant.MIN;
                            if (now.isAfter(expiresAt)) {
                                return Optional.<Object>empty();
                            }
                            String res = rs.getString("result");
                            return Optional.ofNullable((Object) res);
                        },
                        key,
                        STATUS_PROCESSED)
                .stream()
                .findFirst()
                .flatMap(opt -> opt);
    }

    @Override
    public void releaseLock(String key) {
        Objects.requireNonNull(key, "Key must not be null");

        jdbcTemplate.update(RELEASE_LOCK_SQL, key, STATUS_PROCESSING);
    }
}
