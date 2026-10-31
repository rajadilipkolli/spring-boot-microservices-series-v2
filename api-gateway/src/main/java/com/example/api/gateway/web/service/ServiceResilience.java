/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.api.gateway.web.service;

import com.example.api.gateway.model.ServiceType;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class ServiceResilience {

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final BulkheadRegistry bulkheadRegistry;

    public ServiceResilience(
            CircuitBreakerRegistry circuitBreakerRegistry, BulkheadRegistry bulkheadRegistry) {

        this.circuitBreakerRegistry = Objects.requireNonNull(circuitBreakerRegistry);

        this.bulkheadRegistry = Objects.requireNonNull(bulkheadRegistry);
    }

    public CircuitBreaker circuitBreaker(ServiceType serviceType) {

        return circuitBreakerRegistry.circuitBreaker(serviceType.getId().toLowerCase());
    }

    public Bulkhead bulkhead(ServiceType serviceType) {

        return bulkheadRegistry.bulkhead(serviceType.getId().toLowerCase());
    }
}
