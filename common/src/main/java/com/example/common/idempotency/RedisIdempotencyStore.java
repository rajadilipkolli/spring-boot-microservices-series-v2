/***
 * <p>
 * Licensed under MIT License Copyright (c) 2021-2026 Raja Kolli.
 * </p>
 ***/

package com.example.common.idempotency;

import java.time.Duration;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * Distributed implementation of {@link IdempotencyStore} backed by Spring Data Redis. Uses atomic
 * Redis operations (SET NX EX, Lua scripts) for distributed locking and state transitions.
 */
public class RedisIdempotencyStore implements IdempotencyStore {

    private static final String LOCK_PREFIX = "idempotency:lock:";
    private static final String RESULT_PREFIX = "idempotency:result:";
    private static final String LOCK_VALUE = "IN_FLIGHT";
    private static final String NULL_SENTINEL = "__NULL__";

    // Lua script to release lock only if the current value is still IN_FLIGHT
    private static final String RELEASE_LOCK_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('del', KEYS[1]) "
                    + "else "
                    + "return 0 "
                    + "end";

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<Long> releaseLockRedisScript;

    public RedisIdempotencyStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate =
                Objects.requireNonNull(redisTemplate, "StringRedisTemplate must not be null");
        this.releaseLockRedisScript = new DefaultRedisScript<>(RELEASE_LOCK_SCRIPT, Long.class);
    }

    @Override
    public boolean acquireLock(String key, Duration ttl) {
        Objects.requireNonNull(key, "Key must not be null");
        Objects.requireNonNull(ttl, "TTL must not be null");

        // If result already exists, lock cannot be acquired
        Boolean hasResult = redisTemplate.hasKey(resultKey(key));
        if (Boolean.TRUE.equals(hasResult)) {
            return false;
        }

        // Atomically set lock if not already exists (SET NX EX)
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey(key), LOCK_VALUE, ttl);
        return Boolean.TRUE.equals(acquired);
    }

    @Override
    public void markProcessed(String key, Object result, Duration ttl) {
        Objects.requireNonNull(key, "Key must not be null");
        Objects.requireNonNull(ttl, "TTL must not be null");

        String serializedResult = result == null ? NULL_SENTINEL : result.toString();

        // Store result with TTL
        redisTemplate.opsForValue().set(resultKey(key), serializedResult, ttl);

        // Remove the lock
        redisTemplate.delete(lockKey(key));
    }

    @Override
    public Optional<Object> getProcessedResult(String key) {
        Objects.requireNonNull(key, "Key must not be null");

        String val = redisTemplate.opsForValue().get(resultKey(key));
        if (val == null) {
            return Optional.empty();
        }

        if (NULL_SENTINEL.equals(val)) {
            return Optional.empty();
        }

        return Optional.of(val);
    }

    @Override
    public void releaseLock(String key) {
        Objects.requireNonNull(key, "Key must not be null");

        redisTemplate.execute(
                releaseLockRedisScript, Collections.singletonList(lockKey(key)), LOCK_VALUE);
    }

    private String lockKey(String key) {
        return LOCK_PREFIX + key;
    }

    private String resultKey(String key) {
        return RESULT_PREFIX + key;
    }
}
