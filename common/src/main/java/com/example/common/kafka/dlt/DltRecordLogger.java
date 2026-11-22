/***
 * <p>
 * Licensed under MIT License Copyright (c) 2026 Raja Kolli.
 * </p>
 ***/

package com.example.common.kafka.dlt;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;
import java.util.function.ToLongFunction;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.support.KafkaHeaders;

/** Diagnostic logger and metric recorder for Dead Letter Topic (DLT) messages. */
public class DltRecordLogger {

    private static final Logger log = LoggerFactory.getLogger(DltRecordLogger.class);

    private final DltMetrics dltMetrics;
    private final String defaultServiceName;

    public DltRecordLogger(DltMetrics dltMetrics, String defaultServiceName) {
        this.dltMetrics = Objects.requireNonNull(dltMetrics, "dltMetrics must not be null");
        this.defaultServiceName =
                Objects.requireNonNull(defaultServiceName, "defaultServiceName must not be null");
    }

    /**
     * Constructs {@link DltRecordPayload} from a ConsumerRecord, records metrics, and logs error
     * diagnostics.
     *
     * @param record Kafka consumer record
     * @return constructed DltRecordPayload
     */
    public DltRecordPayload processAndLog(ConsumerRecord<?, ?> record) {
        return processAndLog(record, this.defaultServiceName);
    }

    /**
     * Constructs {@link DltRecordPayload} from a ConsumerRecord with service override, records
     * metrics, and logs diagnostics.
     *
     * @param record Kafka consumer record
     * @param serviceName service name
     * @return constructed DltRecordPayload
     */
    public DltRecordPayload processAndLog(ConsumerRecord<?, ?> record, String serviceName) {
        Headers headers = record.headers();
        String originalTopic =
                getHeaderAsString(
                        headers, KafkaHeaders.DLT_ORIGINAL_TOPIC, KafkaHeaders.ORIGINAL_TOPIC);
        if (originalTopic == null) {
            originalTopic = record.topic();
        }

        int originalPartition =
                (int)
                        getNumericHeader(
                                headers,
                                KafkaHeaders.DLT_ORIGINAL_PARTITION,
                                KafkaHeaders.ORIGINAL_PARTITION,
                                record.partition(),
                                Integer.BYTES,
                                Integer::parseInt,
                                ByteBuffer::getInt);
        long originalOffset =
                getNumericHeader(
                        headers,
                        KafkaHeaders.DLT_ORIGINAL_OFFSET,
                        KafkaHeaders.ORIGINAL_OFFSET,
                        record.offset(),
                        Long.BYTES,
                        Long::parseLong,
                        ByteBuffer::getLong);
        String exceptionClass =
                getHeaderAsString(
                        headers, KafkaHeaders.DLT_EXCEPTION_FQCN, KafkaHeaders.EXCEPTION_FQCN);
        String exceptionMessage =
                getHeaderAsString(
                        headers,
                        KafkaHeaders.DLT_EXCEPTION_MESSAGE,
                        KafkaHeaders.EXCEPTION_MESSAGE);
        String stackTrace =
                getHeaderAsString(
                        headers,
                        KafkaHeaders.DLT_EXCEPTION_STACKTRACE,
                        KafkaHeaders.EXCEPTION_STACKTRACE);
        Object value = record.value();
        String payload =
                value instanceof byte[] bytes
                        ? new String(bytes, StandardCharsets.UTF_8)
                        : Objects.toString(value, null);

        DltRecordPayload dltPayload =
                DltRecordPayload.builder()
                        .originalTopic(originalTopic)
                        .originalPartition(originalPartition)
                        .originalOffset(originalOffset)
                        .exceptionClass(exceptionClass)
                        .exceptionMessage(exceptionMessage)
                        .stackTrace(stackTrace)
                        .payload(payload)
                        .failedAt(Instant.now())
                        .serviceName(serviceName)
                        .build();

        dltMetrics.recordDltRecord(originalTopic, exceptionClass, serviceName);

        log.error(
                "Dead-letter record encountered in service [{}] from topic [{}] partition [{}] offset [{}]. Exception: [{}: {}]. Payload: {}",
                serviceName,
                originalTopic,
                originalPartition,
                originalOffset,
                exceptionClass != null ? exceptionClass : "UnknownException",
                exceptionMessage != null ? exceptionMessage : "No exception message",
                payload);

        return dltPayload;
    }

    /**
     * Constructs {@link DltRecordPayload} when message string and topic are available directly
     * (e.g. from @DltHandler).
     *
     * @param payload message payload string
     * @param topic Kafka topic
     * @param exception root exception or null
     * @return constructed DltRecordPayload
     */
    public DltRecordPayload processAndLog(
            @Nullable String payload, @Nullable String topic, @Nullable Throwable exception) {
        return processAndLog(payload, topic, exception, this.defaultServiceName);
    }

    /**
     * Constructs {@link DltRecordPayload} with explicit service name.
     *
     * @param payload message payload string
     * @param topic Kafka topic
     * @param exception root exception or null
     * @param serviceName service name
     * @return constructed DltRecordPayload
     */
    public DltRecordPayload processAndLog(
            @Nullable String payload,
            @Nullable String topic,
            @Nullable Throwable exception,
            String serviceName) {
        String exceptionClass = exception != null ? exception.getClass().getName() : null;
        String exceptionMessage = exception != null ? exception.getMessage() : null;
        String stackTrace = exception != null ? getStackTraceAsString(exception) : null;

        DltRecordPayload dltPayload =
                DltRecordPayload.builder()
                        .originalTopic(topic)
                        .originalPartition(-1)
                        .originalOffset(-1L)
                        .exceptionClass(exceptionClass)
                        .exceptionMessage(exceptionMessage)
                        .stackTrace(stackTrace)
                        .payload(payload)
                        .failedAt(Instant.now())
                        .serviceName(serviceName)
                        .build();

        dltMetrics.recordDltRecord(topic, exceptionClass, serviceName);

        log.error(
                "Dead-letter record encountered in service [{}] from topic [{}]. Exception: [{}: {}]. Payload: {}",
                serviceName,
                topic != null ? topic : "unknown",
                exceptionClass != null ? exceptionClass : "UnknownException",
                exceptionMessage != null ? exceptionMessage : "No exception message",
                payload,
                exception);

        return dltPayload;
    }

    private static @Nullable byte[] getHeader(Headers headers, String name) {
        Header header = headers.lastHeader(name);
        return header == null ? null : header.value();
    }

    private static @Nullable String getHeaderAsString(
            Headers headers, String dltName, String retryName) {
        byte[] bytes = getHeader(headers, dltName);
        if (bytes == null) {
            bytes = getHeader(headers, retryName);
        }
        return bytes == null ? null : new String(bytes, StandardCharsets.UTF_8);
    }

    private static long getNumericHeader(
            Headers headers,
            String dltName,
            String retryName,
            long defaultValue,
            int binaryWidth,
            ToLongFunction<String> parseDecimal,
            ToLongFunction<ByteBuffer> readBinary) {
        // The dead-letter recoverer writes standard DLT coordinates as fixed-width binary.
        byte[] bytes = getHeader(headers, dltName);
        if (bytes != null && bytes.length == binaryWidth) {
            return readBinary.applyAsLong(ByteBuffer.wrap(bytes));
        }

        bytes = getHeader(headers, retryName);
        if (bytes == null) {
            return defaultValue;
        }
        // Retry-topic headers also carry fixed-width binary coordinates. Prefer decimal
        // text for these names to support producers that send coordinates as strings.
        try {
            return parseDecimal.applyAsLong(new String(bytes, StandardCharsets.UTF_8));
        } catch (NumberFormatException ignored) {
            return bytes.length == binaryWidth
                    ? readBinary.applyAsLong(ByteBuffer.wrap(bytes))
                    : defaultValue;
        }
    }

    private String getStackTraceAsString(Throwable throwable) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        return sw.toString();
    }

    public DltMetrics getDltMetrics() {
        return dltMetrics;
    }

    public String getDefaultServiceName() {
        return defaultServiceName;
    }
}
