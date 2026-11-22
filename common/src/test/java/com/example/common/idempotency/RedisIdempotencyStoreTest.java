/***
 * <p>
 * Licensed under MIT License Copyright (c) 2021-2026 Raja Kolli.
 * </p>
 ***/

package com.example.common.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Collections;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

class RedisIdempotencyStoreTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private RedisIdempotencyStore store;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        store = new RedisIdempotencyStore(redisTemplate);
    }

    @Test
    void shouldAcquireLockWhenNoResultAndNotLocked() {
        when(redisTemplate.hasKey("idempotency:result:test-key")).thenReturn(false);
        when(valueOperations.setIfAbsent(
                        "idempotency:lock:test-key", "IN_FLIGHT", Duration.ofSeconds(30)))
                .thenReturn(true);

        boolean acquired = store.acquireLock("test-key", Duration.ofSeconds(30));

        assertThat(acquired).isTrue();
    }

    @Test
    void shouldNotAcquireLockWhenResultAlreadyExists() {
        when(redisTemplate.hasKey("idempotency:result:test-key")).thenReturn(true);

        boolean acquired = store.acquireLock("test-key", Duration.ofSeconds(30));

        assertThat(acquired).isFalse();
    }

    @Test
    void shouldNotAcquireLockWhenLockAlreadyHeld() {
        when(redisTemplate.hasKey("idempotency:result:test-key")).thenReturn(false);
        when(valueOperations.setIfAbsent(
                        "idempotency:lock:test-key", "IN_FLIGHT", Duration.ofSeconds(30)))
                .thenReturn(false);

        boolean acquired = store.acquireLock("test-key", Duration.ofSeconds(30));

        assertThat(acquired).isFalse();
    }

    @Test
    void shouldMarkProcessedAndRemoveLock() {
        store.markProcessed("test-key", "SUCCESS_RESPONSE", Duration.ofMinutes(1));

        verify(valueOperations)
                .set("idempotency:result:test-key", "SUCCESS_RESPONSE", Duration.ofMinutes(1));
        verify(redisTemplate).delete("idempotency:lock:test-key");
    }

    @Test
    void shouldGetProcessedResult() {
        when(valueOperations.get("idempotency:result:test-key")).thenReturn("CACHED_VAL");

        Optional<Object> result = store.getProcessedResult("test-key");

        assertThat(result).isPresent().contains("CACHED_VAL");
    }

    @Test
    void shouldReturnEmptyWhenResultNotFound() {
        when(valueOperations.get("idempotency:result:test-key")).thenReturn(null);

        Optional<Object> result = store.getProcessedResult("test-key");

        assertThat(result).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldReleaseLockUsingScript() {
        store.releaseLock("test-key");

        verify(redisTemplate)
                .execute(
                        any(RedisScript.class),
                        eq(Collections.singletonList("idempotency:lock:test-key")),
                        eq("IN_FLIGHT"));
    }
}
