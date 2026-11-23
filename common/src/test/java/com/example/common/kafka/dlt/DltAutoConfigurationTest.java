/***
 * <p>
 * Licensed under MIT License Copyright (c) 2026 Raja Kolli.
 * </p>
 ***/

package com.example.common.kafka.dlt;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledInNativeImage;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.micrometer.metrics.autoconfigure.CompositeMeterRegistryAutoConfiguration;
import org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration;
import org.springframework.boot.micrometer.metrics.autoconfigure.export.simple.SimpleMetricsExportAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

// ApplicationContextRunner requires runtime configuration metadata and a filtered class loader.
@DisabledInNativeImage
class DltAutoConfigurationTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(DltAutoConfiguration.class));

    @Test
    void registersDltBeansAfterBootCreatesTheRegistry() {
        runner.withConfiguration(
                        AutoConfigurations.of(
                                MetricsAutoConfiguration.class,
                                SimpleMetricsExportAutoConfiguration.class,
                                CompositeMeterRegistryAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(SimpleMeterRegistry.class);
                    assertThat(context).hasSingleBean(DltMetrics.class);
                    assertThat(context).hasSingleBean(DltRecordLogger.class);
                });
    }

    @Test
    void backsOffWhenKafkaIsUnavailable() {
        runner.withBean(SimpleMeterRegistry.class)
                .withClassLoader(new FilteredClassLoader("org.apache.kafka"))
                .run(context -> {
                    assertThat(context).doesNotHaveBean(DltMetrics.class);
                    assertThat(context).doesNotHaveBean(DltRecordLogger.class);
                });
    }
}
