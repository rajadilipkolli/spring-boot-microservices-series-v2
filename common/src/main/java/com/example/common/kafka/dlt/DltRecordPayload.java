/***
 * <p>
 * Licensed under MIT License Copyright (c) 2026 Raja Kolli.
 * </p>
 ***/

package com.example.common.kafka.dlt;

import java.io.Serializable;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Standard payload representing dead-letter topic record metadata and diagnostics.
 *
 * @param originalTopic original Kafka topic name
 * @param originalPartition original partition number
 * @param originalOffset original offset within the partition
 * @param exceptionClass class name of the root exception that led to DLT routing
 * @param exceptionMessage error message from the exception
 * @param stackTrace formatted stack trace of the exception
 * @param payload original message payload as a string
 * @param failedAt instant when the failure was recorded
 * @param serviceName name of the service handling/routing the record
 */
public record DltRecordPayload(
        @Nullable String originalTopic,
        int originalPartition,
        long originalOffset,
        @Nullable String exceptionClass,
        @Nullable String exceptionMessage,
        @Nullable String stackTrace,
        @Nullable String payload,
        @Nullable Instant failedAt,
        @Nullable String serviceName)
        implements Serializable {

    public DltRecordPayload {
        if (failedAt == null) {
            failedAt = Instant.now();
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private @Nullable String originalTopic;
        private int originalPartition = -1;
        private long originalOffset = -1L;
        private @Nullable String exceptionClass;
        private @Nullable String exceptionMessage;
        private @Nullable String stackTrace;
        private @Nullable String payload;
        private @Nullable Instant failedAt;
        private @Nullable String serviceName;

        public Builder originalTopic(@Nullable String originalTopic) {
            this.originalTopic = originalTopic;
            return this;
        }

        public Builder originalPartition(int originalPartition) {
            this.originalPartition = originalPartition;
            return this;
        }

        public Builder originalOffset(long originalOffset) {
            this.originalOffset = originalOffset;
            return this;
        }

        public Builder exceptionClass(@Nullable String exceptionClass) {
            this.exceptionClass = exceptionClass;
            return this;
        }

        public Builder exceptionMessage(@Nullable String exceptionMessage) {
            this.exceptionMessage = exceptionMessage;
            return this;
        }

        public Builder stackTrace(@Nullable String stackTrace) {
            this.stackTrace = stackTrace;
            return this;
        }

        public Builder payload(@Nullable String payload) {
            this.payload = payload;
            return this;
        }

        public Builder failedAt(@Nullable Instant failedAt) {
            this.failedAt = failedAt;
            return this;
        }

        public Builder serviceName(@Nullable String serviceName) {
            this.serviceName = serviceName;
            return this;
        }

        public DltRecordPayload build() {
            return new DltRecordPayload(
                    originalTopic,
                    originalPartition,
                    originalOffset,
                    exceptionClass,
                    exceptionMessage,
                    stackTrace,
                    payload,
                    failedAt,
                    serviceName);
        }
    }
}
