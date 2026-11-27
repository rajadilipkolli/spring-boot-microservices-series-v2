/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.api.gateway.config;

import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedBulkheadMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ResilienceConfiguration {

    @Bean
    TaggedCircuitBreakerMetrics circuitBreakerMetrics(
            CircuitBreakerRegistry registry, MeterRegistry meterRegistry) {

        var metrics = TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(registry);

        metrics.bindTo(meterRegistry);

        return metrics;
    }

    @Bean
    TaggedBulkheadMetrics bulkheadMetrics(BulkheadRegistry registry, MeterRegistry meterRegistry) {

        var metrics = TaggedBulkheadMetrics.ofBulkheadRegistry(registry);

        metrics.bindTo(meterRegistry);

        return metrics;
    }
}
