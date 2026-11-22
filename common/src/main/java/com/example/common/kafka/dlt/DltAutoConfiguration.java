/***
 * <p>
 * Licensed under MIT License Copyright (c) 2026 Raja Kolli.
 * </p>
 ***/

package com.example.common.kafka.dlt;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/** Auto-configuration for the Dead Letter Topic (DLT) Engine. */
@AutoConfiguration
@ConditionalOnClass(MeterRegistry.class)
@ConditionalOnBean(MeterRegistry.class)
@ConditionalOnProperty(
        prefix = "app.kafka.dlt",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class DltAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public DltMetrics dltMetrics(MeterRegistry meterRegistry) {
        return new DltMetrics(meterRegistry);
    }

    @Bean
    @ConditionalOnMissingBean
    public DltRecordLogger dltRecordLogger(
            DltMetrics dltMetrics,
            @Value("${spring.application.name:unknown-service}") String serviceName) {
        return new DltRecordLogger(dltMetrics, serviceName);
    }
}
