/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.api.gateway.web.service;

import com.example.api.gateway.model.ServiceType;
import com.example.api.gateway.observability.GatewayMetrics;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.Exceptions;
import reactor.util.retry.Retry;

/**
 * Defines retry behavior for transient downstream failures.
 *
 * <p>Maximum retries: 3 Initial backoff: 500 milliseconds Backoff multiplier: exponential Jitter:
 * 50 percent
 */
@Component
public class RetryPolicy {

    private static final Logger LOGGER = LoggerFactory.getLogger(RetryPolicy.class);

    private static final long MAX_RETRIES = 3;

    private static final Duration INITIAL_BACKOFF = Duration.ofMillis(500);

    private static final Duration MAX_BACKOFF = Duration.ofSeconds(5);

    private static final double JITTER = 0.5;

    private final GatewayMetrics gatewayMetrics;

    public RetryPolicy(GatewayMetrics gatewayMetrics) {
        this.gatewayMetrics = gatewayMetrics;
    }

    /**
     * Creates a retry specification for the service. Timeout and connection failures and HTTP 429,
     * 502, 503, and 504 responses are eligible for retry. Each retry increments the service metric;
     * exhaustion propagates the last failure.
     */
    public Retry create(ServiceType serviceType) {

        return Retry.backoff(MAX_RETRIES, INITIAL_BACKOFF)
                .maxBackoff(MAX_BACKOFF)
                .jitter(JITTER)
                .filter(this::isRetryable)
                .doBeforeRetry(
                        signal -> {
                            gatewayMetrics.incrementRetry(serviceType);
                        })
                .onRetryExhaustedThrow(
                        (spec, signal) -> {
                            LOGGER.error(
                                    "Downstream retries exhausted. attempts={}",
                                    signal.totalRetries());
                            return signal.failure();
                        });
    }

    /** Determines whether a failure is transient. */
    private boolean isRetryable(Throwable throwable) {

        Throwable exception = Exceptions.unwrap(throwable);

        if (isTimeout(exception)) {
            return true;
        }

        if (exception instanceof WebClientResponseException responseException) {
            return isRetryableStatus(responseException.getStatusCode().value());
        }

        if (throwable instanceof io.github.resilience4j.circuitbreaker.CallNotPermittedException
                || throwable instanceof io.github.resilience4j.bulkhead.BulkheadFullException) {
            return false;
        }

        return hasRetryableConnectionCause(exception);
    }

    /** Retry only explicitly selected upstream HTTP statuses. */
    private boolean isRetryableStatus(int status) {

        return status == 429 || status == 502 || status == 503 || status == 504;
    }

    /** Checks the exception cause chain for transient network failures. */
    private boolean hasRetryableConnectionCause(Throwable throwable) {

        for (Throwable current = throwable; current != null; current = current.getCause()) {

            if (current instanceof ConnectException
                    || current instanceof SocketTimeoutException
                    || current instanceof SocketException) {
                return true;
            }
        }

        return false;
    }

    /** Checks whether the exception represents a timeout. */
    private boolean isTimeout(Throwable throwable) {

        for (Throwable current = throwable; current != null; current = current.getCause()) {

            if (current instanceof TimeoutException
                    || current instanceof SocketTimeoutException
                    || current instanceof io.netty.handler.timeout.TimeoutException) {
                return true;
            }
        }

        return false;
    }
}
