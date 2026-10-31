/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.api.gateway.web.service;

import com.example.api.gateway.config.GatewayProperties;
import com.example.api.gateway.exception.ServiceCallException;
import com.example.api.gateway.model.ServiceResult;
import com.example.api.gateway.model.ServiceType;
import com.example.api.gateway.observability.GatewayMetrics;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.reactor.bulkhead.operator.BulkheadOperator;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import java.net.URI;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

@Service
public class MicroserviceClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(MicroserviceClient.class);

    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    private final WebClient webClient;
    private final RetryPolicy retryPolicy;
    private final GatewayProperties properties;
    private final ServiceResilience serviceResilience;
    private final GatewayMetrics gatewayMetrics;

    public MicroserviceClient(
            WebClient webClient,
            RetryPolicy retryPolicy,
            GatewayProperties properties,
            ServiceResilience serviceResilience,
            GatewayMetrics gatewayMetrics) {

        this.webClient = Objects.requireNonNull(webClient);
        this.retryPolicy = Objects.requireNonNull(retryPolicy);
        this.properties = Objects.requireNonNull(properties);
        this.serviceResilience = Objects.requireNonNull(serviceResilience);
        this.gatewayMetrics = gatewayMetrics;
    }

    /**
     * Posts to the configured service with the batch ID as the idempotency key. Transient failures
     * are retried; a successful empty response body is returned as an empty string.
     *
     * @param batchId nonblank idempotency key shared by the workflow's service calls
     * @param batchSize optional positive batch size sent as a query parameter
     * @return the service status and body, or a reactive error; downstream call failures are
     *     wrapped in {@link ServiceCallException}, while request configuration errors propagate
     *     directly. A blank batch ID or nonpositive batch size produces an {@link
     *     IllegalArgumentException}.
     * @throws NullPointerException if the service type or batch ID is null
     */
    public Mono<ServiceResult> call(ServiceType serviceType, String batchId, Integer batchSize) {

        Objects.requireNonNull(serviceType, "Service type is required");
        Objects.requireNonNull(batchId, "Batch ID is required");

        if (batchId.isBlank()) {
            return Mono.error(new IllegalArgumentException("Batch ID must not be blank"));
        }

        if (batchSize != null && batchSize < 1) {
            return Mono.error(new IllegalArgumentException("Batch size must be positive"));
        }

        return Mono.defer(
                () -> {
                    URI uri = buildRequestUri(serviceType, batchSize);

                    var circuitBreaker = serviceResilience.circuitBreaker(serviceType);

                    Bulkhead bulkhead = serviceResilience.bulkhead(serviceType);

                    var sample = gatewayMetrics.startTimer();

                    return webClient
                            .post()
                            .uri(uri)
                            .header(IDEMPOTENCY_KEY, batchId)
                            .exchangeToMono(this::handleResponse)
                            .timeout(properties.getRequestTimeout())

                            // Every attempt acquires and releases its own permit.
                            .transformDeferred(BulkheadOperator.of(bulkhead))

                            // Retry transient failures.
                            .retryWhen(retryPolicy.create(serviceType))

                            // Circuit breaker sees the final retry outcome.
                            .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                            .doOnSuccess(
                                    _ -> {
                                        gatewayMetrics.recordServiceCall(
                                                sample, serviceType, "success");
                                    })
                            .doOnError(
                                    exception -> {
                                        LOGGER.warn(
                                                "Downstream failure. service={}, batchId={}, type={}",
                                                serviceType.getId(),
                                                batchId,
                                                exception.getClass().getSimpleName());
                                        gatewayMetrics.recordServiceCall(
                                                sample, serviceType, "failure");

                                        gatewayMetrics.incrementFailure(serviceType);
                                    })
                            .onErrorMap(
                                    exception -> !(exception instanceof ServiceCallException),
                                    exception -> new ServiceCallException(serviceType, exception));
                });
    }

    private URI buildRequestUri(ServiceType serviceType, Integer batchSize) {

        UriComponentsBuilder builder =
                UriComponentsBuilder.fromUriString(properties.getServiceUrl(serviceType));

        if (batchSize != null) {
            builder.queryParam("batchSize", batchSize);
        }

        return builder.build().encode().toUri();
    }

    private Mono<ServiceResult> handleResponse(ClientResponse response) {

        HttpStatusCode status = response.statusCode();

        if (status.is2xxSuccessful()) {
            return response.bodyToMono(String.class)
                    .defaultIfEmpty("")
                    .map(body -> new ServiceResult(status.value(), body));
        }

        return response.createException().flatMap(Mono::error);
    }
}
