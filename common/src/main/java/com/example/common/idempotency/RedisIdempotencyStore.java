/***
 * <p>
 * Licensed under MIT License Copyright (c) 2021-2026 Raja Kolli.
 * </p>
 ***/

package com.example.common.idempotency;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/** One expiring Redis string per key, shared by the claim and its result. */
public class RedisIdempotencyStore implements IdempotencyStore {
    private static final DefaultRedisScript<Long> ACQUIRE =
            script(
                    """
                    if redis.call('set', KEYS[1], ARGV[1], 'NX', 'PX', ARGV[2]) then return 1 end
                    return 0
                    """);
    private static final DefaultRedisScript<Long> COMPLETE =
            script(
                    """
                    if redis.call('get', KEYS[1]) ~= ARGV[1] then return 0 end
                    redis.call('set', KEYS[1], ARGV[2], 'PX', ARGV[3])
                    return 1
                    """);
    private static final DefaultRedisScript<Long> RELEASE =
            script(
                    """
                    if redis.call('get', KEYS[1]) ~= ARGV[1] then return 0 end
                    return redis.call('del', KEYS[1])
                    """);

    private final StringRedisTemplate redis;

    public RedisIdempotencyStore(StringRedisTemplate redis) {
        this.redis = Objects.requireNonNull(redis);
    }

    private static DefaultRedisScript<Long> script(String lua) {
        return new DefaultRedisScript<>(lua, Long.class);
    }

    private static String key(String key) {
        return "idempotency:state:" + Objects.requireNonNull(key);
    }

    @Override
    public Optional<String> acquireLock(String key, Duration ttl) {
        String owner = UUID.randomUUID().toString();
        return Long.valueOf(1)
                        .equals(
                                redis.execute(
                                        ACQUIRE,
                                        List.of(key(key)),
                                        "P" + owner,
                                        Long.toString(ttl.toMillis())))
                ? Optional.of(owner)
                : Optional.empty();
    }

    @Override
    public boolean markProcessed(String key, String owner, String result, Duration ttl) {
        return Long.valueOf(1)
                .equals(
                        redis.execute(
                                COMPLETE,
                                List.of(key(key)),
                                "P" + owner,
                                result == null ? "N" : "V" + result,
                                Long.toString(ttl.toMillis())));
    }

    @Override
    public Snapshot lookup(String key) {
        String value = redis.opsForValue().get(key(key));
        if (value == null) return Snapshot.ABSENT;
        return switch (value.charAt(0)) {
            case 'P' -> Snapshot.PROCESSING;
            case 'N' -> new Snapshot(State.COMPLETED, null);
            case 'V' -> new Snapshot(State.COMPLETED, value.substring(1));
            default -> throw new IllegalStateException("Invalid persisted idempotency state");
        };
    }

    @Override
    public void releaseLock(String key, String owner) {
        redis.execute(RELEASE, List.of(key(key)), "P" + owner);
    }
}
