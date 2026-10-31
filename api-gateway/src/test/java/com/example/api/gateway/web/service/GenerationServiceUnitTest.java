/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.api.gateway.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.api.gateway.config.GatewayProperties;
import com.example.api.gateway.exception.GlobalExceptionHandler;
import com.example.api.gateway.exception.ServiceCallException;
import com.example.api.gateway.exception.WorkflowTimeoutException;
import com.example.api.gateway.model.GenerationResponse;
import com.example.api.gateway.model.ServiceResult;
import com.example.api.gateway.model.ServiceType;
import com.example.api.gateway.observability.GatewayMetrics;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.retry.Retry;

class GenerationServiceUnitTest {

    @Test
    void testWorkflowTimeoutException() {
        WorkflowTimeoutException ex =
                new WorkflowTimeoutException(new TimeoutException("timed out"));
        assertThat(ex.getMessage()).contains("Generation workflow exceeded");
    }

    @Test
    void testServiceCallException() {
        ServiceCallException ex =
                new ServiceCallException(ServiceType.CATALOG, new RuntimeException("fail"));
        assertThat(ex.getServiceType()).isEqualTo(ServiceType.CATALOG);
        assertThat(ex.getUpstreamStatus()).isNull();

        WebClientResponseException webEx =
                WebClientResponseException.create(
                        503, "Unavailable", HttpHeaders.EMPTY, new byte[0], null);
        ServiceCallException exWithStatus = new ServiceCallException(ServiceType.ORDER, webEx);
        assertThat(exWithStatus.getUpstreamStatus()).isEqualTo(503);
    }

    @Test
    void testGlobalExceptionHandler() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        // 1. Timeout Exception -> 504 Gateway Timeout
        ServiceCallException timeoutEx =
                new ServiceCallException(
                        ServiceType.CATALOG,
                        new RuntimeException(new TimeoutException("call timeout")));
        ResponseEntity<GenerationResponse> resp1 =
                handler.handleServiceCallException(timeoutEx).block();
        assertThat(resp1.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);

        // 2. 429 Status -> 503 Service Unavailable
        WebClientResponseException rateLimitEx =
                WebClientResponseException.create(
                        429, "Too Many Requests", HttpHeaders.EMPTY, new byte[0], null);
        ServiceCallException serviceRateLimitEx =
                new ServiceCallException(ServiceType.INVENTORY, rateLimitEx);
        ResponseEntity<GenerationResponse> resp2 =
                handler.handleServiceCallException(serviceRateLimitEx).block();
        assertThat(resp2.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

        // 3. WorkflowTimeoutException -> 504 Gateway Timeout
        ResponseEntity<GenerationResponse> resp3 =
                handler.handleWorkflowTimeout(
                                new WorkflowTimeoutException(new TimeoutException("workflow")))
                        .block();
        assertThat(resp3.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);

        // 4. Unexpected Exception -> 500
        ResponseEntity<GenerationResponse> resp4 =
                handler.handleUnexpectedException(new RuntimeException("boom")).block();
        assertThat(resp4.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

        // 5. Bad Request
        ResponseEntity<GenerationResponse> resp5 =
                handler.handleBadRequest(new IllegalArgumentException("invalid")).block();
        assertThat(resp5.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void testMicroserviceClientValidation() {
        WebClient webClient = mock(WebClient.class);
        RetryPolicy retryPolicy = mock(RetryPolicy.class);
        GatewayProperties properties = new GatewayProperties();
        ServiceResilience resilience = mock(ServiceResilience.class);
        GatewayMetrics metrics = mock(GatewayMetrics.class);

        MicroserviceClient client =
                new MicroserviceClient(webClient, retryPolicy, properties, resilience, metrics);

        StepVerifier.create(client.call(ServiceType.CATALOG, "   ", 10))
                .expectError(IllegalArgumentException.class)
                .verify();

        StepVerifier.create(client.call(ServiceType.CATALOG, "batch-1", -1))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    @Test
    void testRetryPolicyFilter() {
        GatewayMetrics metrics = mock(GatewayMetrics.class);
        RetryPolicy retryPolicy = new RetryPolicy(metrics);
        Retry retry = retryPolicy.create(ServiceType.CATALOG);
        assertThat(retry).isNotNull();

        // Check retryable status codes
        WebClientResponseException r502 =
                WebClientResponseException.create(
                        502, "Bad Gateway", HttpHeaders.EMPTY, new byte[0], null);
        WebClientResponseException r500 =
                WebClientResponseException.create(
                        500, "Internal Server Error", HttpHeaders.EMPTY, new byte[0], null);

        // CircuitBreaker CallNotPermittedException / BulkheadFullException
        CallNotPermittedException cbEx = mock(CallNotPermittedException.class);
        BulkheadFullException bhEx = mock(BulkheadFullException.class);

        // SocketTimeoutException / ConnectException
        SocketTimeoutException timeoutEx = new SocketTimeoutException("timeout");
        ConnectException connectEx = new ConnectException("refused");

        // Verify with StepVerifier retrying
        StepVerifier.withVirtualTime(() -> Mono.error(r502).retryWhen(retry))
                .thenAwait(Duration.ofSeconds(10))
                .expectError()
                .verify();

        StepVerifier.withVirtualTime(() -> Mono.error(timeoutEx).retryWhen(retry))
                .thenAwait(Duration.ofSeconds(10))
                .expectError()
                .verify();

        StepVerifier.withVirtualTime(() -> Mono.error(connectEx).retryWhen(retry))
                .thenAwait(Duration.ofSeconds(10))
                .expectError()
                .verify();

        // Non retryable 500 should fail fast without retries
        StepVerifier.create(Mono.error(r500).retryWhen(retry)).expectError().verify();

        // Non retryable CB / Bulkhead exceptions should fail fast
        StepVerifier.create(Mono.error(cbEx).retryWhen(retry)).expectError().verify();
        StepVerifier.create(Mono.error(bhEx).retryWhen(retry)).expectError().verify();
    }

    @Test
    void testGenerationOrchestratorEdgeCases() {
        MicroserviceClient client = mock(MicroserviceClient.class);
        GatewayProperties properties = new GatewayProperties();
        properties.setDelayBetweenServices(Duration.ZERO);
        properties.setWorkflowTimeout(Duration.ofSeconds(1));
        GatewayMetrics metrics = mock(GatewayMetrics.class);

        GenerationOrchestrator orchestrator =
                new GenerationOrchestrator(client, properties, metrics);

        // 1. Catalog returns 400 (not OK)
        when(client.call(any(), any(), any()))
                .thenReturn(Mono.just(new ServiceResult(400, "Bad input")));

        StepVerifier.create(orchestrator.generate(10))
                .assertNext(
                        resp -> {
                            assertThat(resp.status()).isEqualTo("error");
                            assertThat(resp.message()).isEqualTo("Catalog generation failed");
                        })
                .verifyComplete();

        // 2. Catalog OK, Inventory returns 400 (not OK)
        when(client.call(any(), any(), any()))
                .thenReturn(Mono.just(new ServiceResult(200, "Catalog OK")))
                .thenReturn(Mono.just(new ServiceResult(400, "Inventory failed")));

        StepVerifier.create(orchestrator.generate(10))
                .assertNext(
                        resp -> {
                            assertThat(resp.status()).isEqualTo("error");
                            assertThat(resp.message()).isEqualTo("Inventory generation failed");
                        })
                .verifyComplete();

        // 3. Catalog OK, Inventory OK, Order returns 400 (not OK)
        when(client.call(any(), any(), any()))
                .thenReturn(Mono.just(new ServiceResult(200, "Catalog OK")))
                .thenReturn(Mono.just(new ServiceResult(200, "Inventory OK")))
                .thenReturn(Mono.just(new ServiceResult(400, "Order failed")));

        StepVerifier.create(orchestrator.generate(10))
                .assertNext(
                        resp -> {
                            assertThat(resp.status()).isEqualTo("error");
                            assertThat(resp.message()).isEqualTo("Order generation failed");
                        })
                .verifyComplete();

        // 4. Workflow timeout
        when(client.call(any(), any(), any())).thenReturn(Mono.never()); // Hangs indefinitely

        StepVerifier.create(orchestrator.generate(10))
                .expectError(WorkflowTimeoutException.class)
                .verify();
    }
}
