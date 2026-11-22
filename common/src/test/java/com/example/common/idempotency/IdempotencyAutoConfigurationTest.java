/***
 * <p>
 * Licensed under MIT License Copyright (c) 2021-2026 Raja Kolli.
 * </p>
 ***/

package com.example.common.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

class IdempotencyAutoConfigurationTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(IdempotencyAutoConfiguration.class));

    @Configuration
    static class RedisMockConfiguration {
        @Bean
        public StringRedisTemplate stringRedisTemplate() {
            return mock(StringRedisTemplate.class);
        }
    }

    @Configuration
    static class JdbcMockConfiguration {
        @Bean
        public DataSource dataSource() {
            return mock(DataSource.class);
        }

        @Bean
        public JdbcTemplate jdbcTemplate() {
            return mock(JdbcTemplate.class);
        }
    }

    @Test
    void shouldRegisterInMemoryStoreByDefault() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(IdempotencyStore.class);
            assertThat(context.getBean(IdempotencyStore.class))
                    .isInstanceOf(InMemoryIdempotencyStore.class);
            assertThat(context).hasSingleBean(IdempotentAspect.class);
        });
    }

    @Test
    void shouldRegisterRedisStoreWhenConfigured() {
        runner.withUserConfiguration(RedisMockConfiguration.class)
                .withPropertyValues("app.idempotency.store=redis")
                .run(context -> {
                    assertThat(context).hasSingleBean(IdempotencyStore.class);
                    assertThat(context.getBean(IdempotencyStore.class))
                            .isInstanceOf(RedisIdempotencyStore.class);
                    assertThat(context).hasSingleBean(IdempotentAspect.class);
                });
    }

    @Test
    void shouldRegisterJdbcStoreWhenConfiguredWithStoreProperty() {
        runner.withUserConfiguration(JdbcMockConfiguration.class)
                .withPropertyValues("app.idempotency.store=jdbc")
                .run(context -> {
                    assertThat(context).hasSingleBean(IdempotencyStore.class);
                    assertThat(context.getBean(IdempotencyStore.class))
                            .isInstanceOf(JdbcIdempotencyStore.class);
                    assertThat(context).hasSingleBean(IdempotentAspect.class);
                });
    }

    @Test
    void shouldRegisterJdbcStoreAsFallbackWhenDataSourcePresentAndRedisAbsent() {
        runner.withUserConfiguration(JdbcMockConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(IdempotencyStore.class);
                    assertThat(context.getBean(IdempotencyStore.class))
                            .isInstanceOf(JdbcIdempotencyStore.class);
                    assertThat(context).hasSingleBean(IdempotentAspect.class);
                });
    }

    @Test
    void shouldBackOffWhenDisabled() {
        runner.withPropertyValues("app.idempotency.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(IdempotencyStore.class);
                    assertThat(context).doesNotHaveBean(IdempotentAspect.class);
                });
    }
}
