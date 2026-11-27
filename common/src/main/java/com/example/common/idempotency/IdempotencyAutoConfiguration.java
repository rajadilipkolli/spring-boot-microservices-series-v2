/***
 * <p>
 * Licensed under MIT License Copyright (c) 2026 Raja Kolli.
 * </p>
 ***/

package com.example.common.idempotency;

import java.time.Duration;
import javax.sql.DataSource;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/** Auto-configuration for the idempotency engine. */
@AutoConfiguration(
        afterName = {
            "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
            "org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration",
            "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration"
        })
@ConditionalOnProperty(
        prefix = "app.idempotency",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class IdempotencyAutoConfiguration {

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(StringRedisTemplate.class)
    static class RedisConfiguration {
        @Bean
        @ConditionalOnMissingBean(IdempotencyStore.class)
        @ConditionalOnProperty(prefix = "app.idempotency", name = "store", havingValue = "redis")
        public IdempotencyStore redisIdempotencyStore(StringRedisTemplate stringRedisTemplate) {
            return new RedisIdempotencyStore(stringRedisTemplate);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(JdbcTemplate.class)
    static class JdbcConfiguration {
        @Bean
        @ConditionalOnMissingBean(IdempotencyStore.class)
        @ConditionalOnProperty(prefix = "app.idempotency", name = "store", havingValue = "jdbc")
        public IdempotencyStore jdbcIdempotencyStore(JdbcTemplate jdbcTemplate) {
            return new JdbcIdempotencyStore(jdbcTemplate);
        }

        @Bean
        @ConditionalOnMissingBean(
                value = IdempotencyStore.class,
                type = "org.springframework.data.redis.core.StringRedisTemplate")
        @ConditionalOnProperty(
                prefix = "app.idempotency",
                name = "store",
                havingValue = "jdbc",
                matchIfMissing = true)
        @ConditionalOnBean(DataSource.class)
        public IdempotencyStore fallbackJdbcIdempotencyStore(JdbcTemplate jdbcTemplate) {
            return new JdbcIdempotencyStore(jdbcTemplate);
        }
    }

    @Bean
    @ConditionalOnMissingBean(IdempotencyStore.class)
    public InMemoryIdempotencyStore inMemoryIdempotencyStore() {
        return new InMemoryIdempotencyStore();
    }

    @Bean
    @ConditionalOnBean(InMemoryIdempotencyStore.class)
    public DisposableBean idempotencyCleanup(
            InMemoryIdempotencyStore store,
            ObjectProvider<TaskScheduler> schedulers,
            @Value("${app.idempotency.cleanup-interval:PT1M}") String cleanupInterval) {
        Duration interval = DurationStyle.detectAndParse(cleanupInterval);
        var scheduler = schedulers.getIfUnique();
        if (scheduler != null) {
            var task = scheduler.scheduleWithFixedDelay(store::evictExpired, interval);
            return () -> task.cancel(false);
        }
        var local = new ThreadPoolTaskScheduler();
        local.setThreadNamePrefix("idempotency-cleanup-");
        local.initialize();
        try {
            local.scheduleWithFixedDelay(store::evictExpired, interval);
        } catch (RuntimeException e) {
            local.shutdown();
            throw e;
        }
        return local::shutdown;
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "org.aspectj.lang.ProceedingJoinPoint")
    public IdempotentAspect idempotentAspect(IdempotencyStore idempotencyStore) {
        return new IdempotentAspect(idempotencyStore);
    }
}
