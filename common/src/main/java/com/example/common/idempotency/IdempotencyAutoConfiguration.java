/***
 * <p>
 * Licensed under MIT License Copyright (c) 2021-2026 Raja Kolli.
 * </p>
 ***/

package com.example.common.idempotency;

import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

/** Auto-configuration for the idempotency engine. */
@AutoConfiguration
@ConditionalOnProperty(
        prefix = "app.idempotency",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class IdempotencyAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(IdempotencyStore.class)
    @ConditionalOnClass(StringRedisTemplate.class)
    @ConditionalOnProperty(prefix = "app.idempotency", name = "store", havingValue = "redis")
    public IdempotencyStore redisIdempotencyStore(StringRedisTemplate stringRedisTemplate) {
        return new RedisIdempotencyStore(stringRedisTemplate);
    }

    @Bean
    @ConditionalOnMissingBean(IdempotencyStore.class)
    @ConditionalOnClass(JdbcTemplate.class)
    @ConditionalOnProperty(prefix = "app.idempotency", name = "store", havingValue = "jdbc")
    public IdempotencyStore jdbcIdempotencyStore(JdbcTemplate jdbcTemplate) {
        return new JdbcIdempotencyStore(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean({IdempotencyStore.class, StringRedisTemplate.class})
    @ConditionalOnClass(JdbcTemplate.class)
    @ConditionalOnBean(DataSource.class)
    public IdempotencyStore fallbackJdbcIdempotencyStore(JdbcTemplate jdbcTemplate) {
        return new JdbcIdempotencyStore(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean(IdempotencyStore.class)
    public IdempotencyStore inMemoryIdempotencyStore() {
        return new InMemoryIdempotencyStore();
    }

    @Bean
    @ConditionalOnMissingBean
    public IdempotentAspect idempotentAspect(IdempotencyStore idempotencyStore) {
        return new IdempotentAspect(idempotencyStore);
    }
}
