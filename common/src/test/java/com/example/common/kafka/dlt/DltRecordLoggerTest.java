/***
 * <p>
 * Licensed under MIT License Copyright (c) 2026 Raja Kolli.
 * </p>
 ***/

package com.example.common.kafka.dlt;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.KafkaHeaders;

class DltRecordLoggerTest {

    private SimpleMeterRegistry meterRegistry;
    private DltMetrics dltMetrics;
    private DltRecordLogger dltRecordLogger;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        dltMetrics = new DltMetrics(meterRegistry);
        dltRecordLogger = new DltRecordLogger(dltMetrics, "payment-service");
    }

    @Test
    void shouldProcessAndLogConsumerRecordWithHeaders() {
        RecordHeaders headers = new RecordHeaders();
        headers.add(KafkaHeaders.ORIGINAL_TOPIC, "retry-topic".getBytes(StandardCharsets.UTF_8));
        headers.add(KafkaHeaders.ORIGINAL_PARTITION, "2".getBytes(StandardCharsets.UTF_8));
        headers.add(KafkaHeaders.ORIGINAL_OFFSET, "7".getBytes(StandardCharsets.UTF_8));
        headers.add(
                KafkaHeaders.EXCEPTION_FQCN, "retry-exception".getBytes(StandardCharsets.UTF_8));
        headers.add(
                KafkaHeaders.DLT_ORIGINAL_TOPIC, "orders-topic".getBytes(StandardCharsets.UTF_8));
        headers.add(
                KafkaHeaders.DLT_ORIGINAL_PARTITION,
                ByteBuffer.allocate(Integer.BYTES).putInt(1).array());
        // These bytes look like decimal text, but the standard DLT header is binary.
        byte[] offset = "12345678".getBytes(StandardCharsets.UTF_8);
        headers.add(KafkaHeaders.DLT_ORIGINAL_OFFSET, offset);
        headers.add(
                KafkaHeaders.DLT_EXCEPTION_FQCN,
                "java.lang.IllegalArgumentException".getBytes(StandardCharsets.UTF_8));
        headers.add(
                KafkaHeaders.DLT_EXCEPTION_MESSAGE, "Bad request".getBytes(StandardCharsets.UTF_8));
        headers.add(
                KafkaHeaders.DLT_EXCEPTION_STACKTRACE,
                "fake-stack-trace".getBytes(StandardCharsets.UTF_8));

        ConsumerRecord<String, byte[]> record =
                new ConsumerRecord<>(
                        "orders-topic-dlt",
                        0,
                        100L,
                        "key1",
                        "{\"id\":\"café\"}".getBytes(StandardCharsets.UTF_8));
        headers.forEach(h -> record.headers().add(h));

        DltRecordPayload payload = dltRecordLogger.processAndLog(record);

        assertThat(payload.originalTopic()).isEqualTo("orders-topic");
        assertThat(payload.originalPartition()).isEqualTo(1);
        assertThat(payload.originalOffset()).isEqualTo(0x3132333435363738L);
        assertThat(payload.exceptionClass()).isEqualTo("java.lang.IllegalArgumentException");
        assertThat(payload.exceptionMessage()).isEqualTo("Bad request");
        assertThat(payload.stackTrace()).isEqualTo("fake-stack-trace");
        assertThat(payload.payload()).isEqualTo("{\"id\":\"café\"}");
        assertThat(payload.serviceName()).isEqualTo("payment-service");

        Counter counter =
                meterRegistry
                        .find(DltMetrics.METRIC_NAME)
                        .tag(DltMetrics.TAG_TOPIC, "orders-topic")
                        .tag(DltMetrics.TAG_EXCEPTION_CLASS, "java.lang.IllegalArgumentException")
                        .tag(DltMetrics.TAG_SERVICE, "payment-service")
                        .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void readsDecimalRetryCoordinatesEvenWhenTheyMatchTheBinaryWidth() {
        var record = new ConsumerRecord<>("orders-retry", 0, 100L, "key", "body");
        record.headers()
                .add(KafkaHeaders.ORIGINAL_TOPIC, "orders".getBytes(StandardCharsets.UTF_8));
        record.headers()
                .add(KafkaHeaders.ORIGINAL_PARTITION, "1234".getBytes(StandardCharsets.UTF_8));
        record.headers()
                .add(KafkaHeaders.ORIGINAL_OFFSET, "12345678".getBytes(StandardCharsets.UTF_8));

        DltRecordPayload payload = dltRecordLogger.processAndLog(record);

        assertThat(payload.originalTopic()).isEqualTo("orders");
        assertThat(payload.originalPartition()).isEqualTo(1234);
        assertThat(payload.originalOffset()).isEqualTo(12345678L);
        assertThat(payload.payload()).isEqualTo("body");
    }

    @Test
    void readsBinaryRetryCoordinatesAndPreservesNullPayload() {
        var record = new ConsumerRecord<>("orders-retry", 0, 100L, "key", null);
        record.headers()
                .add(
                        KafkaHeaders.ORIGINAL_PARTITION,
                        ByteBuffer.allocate(Integer.BYTES).putInt(4).array());
        record.headers()
                .add(
                        KafkaHeaders.ORIGINAL_OFFSET,
                        ByteBuffer.allocate(Long.BYTES).putLong(42L).array());

        DltRecordPayload payload = dltRecordLogger.processAndLog(record);

        assertThat(payload.originalPartition()).isEqualTo(4);
        assertThat(payload.originalOffset()).isEqualTo(42L);
        assertThat(payload.payload()).isNull();
    }

    @Test
    void shouldProcessAndLogStringPayloadWithException() {
        RuntimeException ex = new RuntimeException("DB down");
        DltRecordPayload payload = dltRecordLogger.processAndLog("{\"id\":2}", "orders-topic", ex);

        assertThat(payload.originalTopic()).isEqualTo("orders-topic");
        assertThat(payload.originalPartition()).isEqualTo(-1);
        assertThat(payload.originalOffset()).isEqualTo(-1L);
        assertThat(payload.exceptionClass()).isEqualTo("java.lang.RuntimeException");
        assertThat(payload.exceptionMessage()).isEqualTo("DB down");
        assertThat(payload.stackTrace()).contains("RuntimeException: DB down");
        assertThat(payload.payload()).isEqualTo("{\"id\":2}");
        assertThat(payload.serviceName()).isEqualTo("payment-service");

        Counter counter =
                meterRegistry
                        .find(DltMetrics.METRIC_NAME)
                        .tag(DltMetrics.TAG_TOPIC, "orders-topic")
                        .tag(DltMetrics.TAG_EXCEPTION_CLASS, "java.lang.RuntimeException")
                        .tag(DltMetrics.TAG_SERVICE, "payment-service")
                        .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void shouldProcessAndLogWithCustomServiceName() {
        DltRecordPayload payload =
                dltRecordLogger.processAndLog("payload", "test-topic", null, "custom-service");

        assertThat(payload.serviceName()).isEqualTo("custom-service");
        assertThat(payload.exceptionClass()).isNull();

        Counter counter =
                meterRegistry
                        .find(DltMetrics.METRIC_NAME)
                        .tag(DltMetrics.TAG_TOPIC, "test-topic")
                        .tag(DltMetrics.TAG_EXCEPTION_CLASS, "unknown")
                        .tag(DltMetrics.TAG_SERVICE, "custom-service")
                        .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }
}
