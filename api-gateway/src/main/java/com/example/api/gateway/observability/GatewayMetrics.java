/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.api.gateway.observability;

import com.example.api.gateway.model.ServiceType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class GatewayMetrics {

    private final MeterRegistry meterRegistry;

    public GatewayMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = Objects.requireNonNull(meterRegistry);
    }

    public Timer.Sample startTimer() {
        return Timer.start(meterRegistry);
    }

    public void recordServiceCall(Timer.Sample sample, ServiceType serviceType, String outcome) {

        sample.stop(
                Timer.builder("gateway.downstream.request.duration")
                        .description("Downstream microservice request duration")
                        .tag("service", serviceType.getId().toLowerCase())
                        .tag("outcome", outcome)
                        .register(meterRegistry));
    }

    public void incrementWorkflow(String outcome) {

        Counter.builder("gateway.generation.workflow")
                .description("Generation workflow outcomes")
                .tag("outcome", outcome)
                .register(meterRegistry)
                .increment();
    }

    public void incrementRetry(ServiceType serviceType) {

        Counter.builder("gateway.downstream.retry")
                .description("Downstream retry attempts")
                .tag("service", serviceType.getId().toLowerCase())
                .register(meterRegistry)
                .increment();
    }

    public void incrementFailure(ServiceType serviceType) {

        Counter.builder("gateway.downstream.failure")
                .description("Downstream service failures")
                .tag("service", serviceType.getId().toLowerCase())
                .register(meterRegistry)
                .increment();
    }
}
