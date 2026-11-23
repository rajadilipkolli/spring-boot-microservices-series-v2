/***
 * <p>
 * Licensed under MIT License Copyright (c) 2021-2026 Raja Kolli.
 * </p>
 ***/

package com.example.common.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import javax.sql.DataSource;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledInNativeImage;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

// ApplicationContextRunner requires runtime configuration metadata and a filtered class loader.
@DisabledInNativeImage
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

    @Test
    void selectsJdbcAfterBootRegistersDataSource() {
        runner.withConfiguration(
                        AutoConfigurations.of(
                                DataSourceAutoConfiguration.class,
                                JdbcTemplateAutoConfiguration.class))
                .withPropertyValues(
                        "spring.datasource.type=org.springframework.jdbc.datasource.SimpleDriverDataSource",
                        "spring.datasource.url=jdbc:postgresql://localhost:5432/testdb",
                        "spring.datasource.username=test",
                        "spring.datasource.password=test")
                .run(context ->
                        assertThat(context.getBean(IdempotencyStore.class))
                                .isInstanceOf(JdbcIdempotencyStore.class));
    }

    @Test
    void handlesMissingOptionalDependencies() {
        runner.withClassLoader(
                        new FilteredClassLoader(
                                "org.springframework.jdbc", "org.springframework.data.redis"))
                .run(context ->
                        assertThat(context.getBean(IdempotencyStore.class))
                                .isInstanceOf(InMemoryIdempotencyStore.class));
    }

    @Test
    void periodicallyEvictsUnreadKeys() {
        runner.withPropertyValues("app.idempotency.cleanup-interval=10ms")
                .run(context -> {
                    var store = context.getBean(InMemoryIdempotencyStore.class);
                    store.acquireLock("unused", Duration.ofMillis(10));
                    Awaitility.await().until(() -> store.size() == 0);
                });
    }
}
