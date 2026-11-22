/***
 * <p>
 * Licensed under MIT License Copyright (c) 2021-2026 Raja Kolli.
 * </p>
 ***/

package com.example.common.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
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
    void distinguishesAllStoredStatesAndLiteralNullSentinel() {
        when(valueOperations.get("idempotency:state:k"))
                .thenReturn(null, "Powner", "N", "V__NULL__");
        assertThat(store.lookup("k")).isEqualTo(IdempotencyStore.Snapshot.ABSENT);
        assertThat(store.lookup("k")).isEqualTo(IdempotencyStore.Snapshot.PROCESSING);
        assertThat(store.lookup("k"))
                .isEqualTo(new IdempotencyStore.Snapshot(IdempotencyStore.State.COMPLETED, null));
        assertThat(store.lookup("k"))
                .isEqualTo(
                        new IdempotencyStore.Snapshot(
                                IdempotencyStore.State.COMPLETED, "__NULL__"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void passesTheAcquiredOwnerAndResultEncodingToRedis() {
        var keys = List.of("idempotency:state:k");
        var ttl = Duration.ofSeconds(5);
        when(redisTemplate.execute(
                        ArgumentMatchers.<RedisScript<Long>>any(),
                        eq(keys),
                        startsWith("P"),
                        eq("5000")))
                .thenReturn(1L, 0L);
        String owner = store.acquireLock("k", ttl).orElseThrow();
        assertThat(store.acquireLock("k", ttl)).isEmpty();
        when(redisTemplate.execute(
                        ArgumentMatchers.<RedisScript<Long>>any(),
                        eq(keys),
                        eq("P" + owner),
                        eq("V__NULL__"),
                        eq("5000")))
                .thenReturn(1L);
        assertThat(store.markProcessed("k", owner, "__NULL__", ttl)).isTrue();
        store.releaseLock("k", owner);
        verify(redisTemplate)
                .execute(ArgumentMatchers.<RedisScript<Long>>any(), eq(keys), eq("P" + owner));
    }
}
