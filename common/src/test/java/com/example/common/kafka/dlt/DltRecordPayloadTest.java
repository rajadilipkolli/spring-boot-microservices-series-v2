/***
 * <p>
 * Licensed under MIT License Copyright (c) 2026 Raja Kolli.
 * </p>
 ***/

package com.example.common.kafka.dlt;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class DltRecordPayloadTest {

    @Test
    void shouldCreatePayloadWithDefaults() {
        DltRecordPayload payload =
                DltRecordPayload.builder()
                        .originalTopic("orders")
                        .originalPartition(2)
                        .originalOffset(105L)
                        .exceptionClass("java.lang.IllegalArgumentException")
                        .exceptionMessage("Invalid payload")
                        .stackTrace("trace...")
                        .payload("{\"orderId\": 1}")
                        .serviceName("payment-service")
                        .build();

        assertThat(payload.originalTopic()).isEqualTo("orders");
        assertThat(payload.originalPartition()).isEqualTo(2);
        assertThat(payload.originalOffset()).isEqualTo(105L);
        assertThat(payload.exceptionClass()).isEqualTo("java.lang.IllegalArgumentException");
        assertThat(payload.exceptionMessage()).isEqualTo("Invalid payload");
        assertThat(payload.stackTrace()).isEqualTo("trace...");
        assertThat(payload.payload()).isEqualTo("{\"orderId\": 1}");
        assertThat(payload.serviceName()).isEqualTo("payment-service");
        assertThat(payload.failedAt()).isNotNull();
    }

    @Test
    void shouldPreserveExplicitFailedAt() {
        Instant fixed = Instant.parse("2026-11-22T10:00:00Z");
        DltRecordPayload payload =
                new DltRecordPayload(
                        "orders",
                        0,
                        10L,
                        "java.lang.RuntimeException",
                        "Boom",
                        "trace",
                        "{}",
                        fixed,
                        "order-service");

        assertThat(payload.failedAt()).isEqualTo(fixed);
    }
}
