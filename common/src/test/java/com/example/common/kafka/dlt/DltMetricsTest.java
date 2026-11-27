/***
 * <p>
 * Licensed under MIT License Copyright (c) 2026 Raja Kolli.
 * </p>
 ***/

package com.example.common.kafka.dlt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DltMetricsTest {

    private SimpleMeterRegistry meterRegistry;
    private DltMetrics dltMetrics;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        dltMetrics = new DltMetrics(meterRegistry);
    }

    @Test
    void shouldRejectNullMeterRegistry() {
        assertThatThrownBy(() -> new DltMetrics(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldRecordDltRecordCounter() {
        dltMetrics.recordDltRecord(
                "orders", "java.lang.IllegalArgumentException", "payment-service");

        Counter counter =
                meterRegistry
                        .find(DltMetrics.METRIC_NAME)
                        .tag(DltMetrics.TAG_TOPIC, "orders")
                        .tag(DltMetrics.TAG_EXCEPTION_CLASS, "java.lang.IllegalArgumentException")
                        .tag(DltMetrics.TAG_SERVICE, "payment-service")
                        .counter();

        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);

        dltMetrics.recordDltRecord(
                "orders", "java.lang.IllegalArgumentException", "payment-service");
        assertThat(counter.count()).isEqualTo(2.0);
    }

    @Test
    void shouldHandleNullTagsWithUnknownFallback() {
        dltMetrics.recordDltRecord(null, null, null);

        Counter counter =
                meterRegistry
                        .find(DltMetrics.METRIC_NAME)
                        .tag(DltMetrics.TAG_TOPIC, "unknown")
                        .tag(DltMetrics.TAG_EXCEPTION_CLASS, "unknown")
                        .tag(DltMetrics.TAG_SERVICE, "unknown")
                        .counter();

        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }
}
