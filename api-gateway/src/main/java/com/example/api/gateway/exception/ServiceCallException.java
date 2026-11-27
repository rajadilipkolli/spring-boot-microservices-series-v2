/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.api.gateway.exception;

import com.example.api.gateway.model.ServiceType;
import java.util.Objects;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/** Represents a failure while communicating with a downstream microservice. */
public class ServiceCallException extends RuntimeException {

    private final ServiceType serviceType;

    public ServiceCallException(ServiceType serviceType, Throwable cause) {

        super(
                "Downstream service call failed: " + Objects.requireNonNull(serviceType).getId(),
                Objects.requireNonNull(cause));

        this.serviceType = serviceType;
    }

    public ServiceType getServiceType() {
        return serviceType;
    }

    /** Returns the first upstream HTTP status in the cause chain, or null if none is present. */
    public Integer getUpstreamStatus() {

        for (Throwable current = getCause(); current != null; current = current.getCause()) {

            if (current instanceof WebClientResponseException exception) {
                return exception.getStatusCode().value();
            }
        }

        return null;
    }
}
