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
        String originalTopic = getHeaderAsString(headers, KafkaHeaders.ORIGINAL_TOPIC);
        if (originalTopic == null) {
            originalTopic = record.topic();
        }

        int originalPartition =
                getHeaderAsInt(headers, KafkaHeaders.ORIGINAL_PARTITION, record.partition());
        long originalOffset =
                getHeaderAsLong(headers, KafkaHeaders.ORIGINAL_OFFSET, record.offset());
        String exceptionClass = getHeaderAsString(headers, KafkaHeaders.EXCEPTION_FQCN);
        String exceptionMessage = getHeaderAsString(headers, KafkaHeaders.EXCEPTION_MESSAGE);
        String stackTrace = getHeaderAsString(headers, KafkaHeaders.EXCEPTION_STACKTRACE);
        String payload = record.value() != null ? String.valueOf(record.value()) : null;

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

    private @Nullable String getHeaderAsString(Headers headers, String headerName) {
        Header header = headers.lastHeader(headerName);
        if (header != null && header.value() != null) {
            return new String(header.value(), StandardCharsets.UTF_8);
        }
        return null;
    }

    private int getHeaderAsInt(Headers headers, String headerName, int defaultValue) {
        Header header = headers.lastHeader(headerName);
        if (header != null && header.value() != null) {
            try {
                if (header.value().length == 4) {
                    return ByteBuffer.wrap(header.value()).getInt();
                }
                return Integer.parseInt(new String(header.value(), StandardCharsets.UTF_8));
            } catch (Exception ignored) {
            }
        }
        return defaultValue;
    }

    private long getHeaderAsLong(Headers headers, String headerName, long defaultValue) {
        Header header = headers.lastHeader(headerName);
        if (header != null && header.value() != null) {
            try {
                if (header.value().length == 8) {
                    return ByteBuffer.wrap(header.value()).getLong();
                }
                return Long.parseLong(new String(header.value(), StandardCharsets.UTF_8));
            } catch (Exception ignored) {
            }
        }
        return defaultValue;
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
