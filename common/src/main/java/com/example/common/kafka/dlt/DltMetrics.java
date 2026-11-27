/***
 * <p>
 * Licensed under MIT License Copyright (c) 2026 Raja Kolli.
 * </p>
 ***/

package com.example.common.kafka.dlt;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Micrometer metrics recorder for Dead Letter Topic (DLT) events. */
public class DltMetrics {

    public static final String METRIC_NAME = "kafka.dlt.records.total";
    public static final String TAG_TOPIC = "topic";
    public static final String TAG_EXCEPTION_CLASS = "exception_class";
    public static final String TAG_SERVICE = "service";

    private final MeterRegistry meterRegistry;

    public DltMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry =
                Objects.requireNonNull(meterRegistry, "meterRegistry must not be null");
    }

    /**
     * Increments the dead letter record counter with topic, exception_class, and service tags.
     *
     * @param topic Kafka topic where failure originated or DLT topic
     * @param exceptionClass exception class name or type
     * @param service service reporting the failure
     */
    public void recordDltRecord(
            @Nullable String topic, @Nullable String exceptionClass, @Nullable String service) {
        Counter.builder(METRIC_NAME)
                .tag(TAG_TOPIC, topic != null ? topic : "unknown")
                .tag(TAG_EXCEPTION_CLASS, exceptionClass != null ? exceptionClass : "unknown")
                .tag(TAG_SERVICE, service != null ? service : "unknown")
                .description("Total number of records routed to dead-letter topics")
                .register(meterRegistry)
                .increment();
    }

    public MeterRegistry getMeterRegistry() {
        return meterRegistry;
    }
}
