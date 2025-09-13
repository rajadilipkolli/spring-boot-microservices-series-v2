/***
<p>
    Licensed under MIT License Copyright (c) 2025 Raja Kolli.
</p>
***/

package com.example.api.gateway.web.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/** Unit tests for the {@link GenerateController} class. */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"rawtypes", "unchecked"})
class GenerateControllerTest {

    @Mock private WebClient.Builder webClientBuilder;
    @Mock private WebClient webClient;
    @Mock private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;
    @Mock private WebClient.RequestHeadersSpec requestHeadersSpec;
    @Mock private WebClient.ResponseSpec responseSpec;

    private GenerateController controller;

    // Reflection helpers to access the record-like GenerationResponse at runtime
    private String getBodyStatus(Object body) {
        try {
            return (String) body.getClass().getMethod("status").invoke(body);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String getBodyMessage(Object body) {
        try {
            return (String) body.getClass().getMethod("message").invoke(body);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private java.util.Map<String, String> getServiceResponses(Object body) {
        try {
            return (java.util.Map<String, String>)
                    body.getClass().getMethod("serviceResponses").invoke(body);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @BeforeEach
    void setup() {
        when(webClientBuilder.build()).thenReturn(webClient);

        // Common mocking for webClient.get().uri(...).retrieve()
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);

        // Use Duration.ZERO for tests to avoid unnecessary delays
        controller = new GenerateController(webClientBuilder, Duration.ZERO, false);
    }

    private Mono<?> invokeGenerate() {
        try {
            java.lang.reflect.Method m = controller.getClass().getMethod("generate");
            return (Mono<?>) m.invoke(controller);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void shouldGenerateDataWhenBothServicesSucceed() {
        ResponseEntity<String> catalogResponse = ResponseEntity.ok("Test catalog data");
        ResponseEntity<String> inventoryResponse = ResponseEntity.ok("Test inventory data");

        when(responseSpec.toEntity(eq(String.class)))
                .thenReturn(Mono.just(catalogResponse))
                .thenReturn(Mono.just(inventoryResponse));

        Mono<?> result = invokeGenerate();

        StepVerifier.create((Mono<ResponseEntity<?>>) result)
                .expectNextMatches(
                        response -> {
                            Object body = response.getBody();
                            return response.getStatusCode() == HttpStatus.OK
                                    && Objects.requireNonNull(getBodyStatus(body)).equals("success")
                                    && Objects.requireNonNull(getBodyMessage(body))
                                            .contains("Generation process completed successfully")
                                    && Objects.requireNonNull(getServiceResponses(body))
                                            .get("catalog")
                                            .equals("Test catalog data")
                                    && Objects.requireNonNull(getServiceResponses(body))
                                            .get("inventory")
                                            .equals("Test inventory data");
                        })
                .verifyComplete();
    }

    @Test
    void shouldHandleCatalogServiceError() {
        WebClientResponseException catalogException =
                new WebClientResponseException(
                        "Catalog Service Error",
                        HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "Internal Server Error from Catalog",
                        HttpHeaders.EMPTY,
                        "Catalog service error body".getBytes(),
                        null);

        when(responseSpec.toEntity(eq(String.class))).thenReturn(Mono.error(catalogException));

        Mono<?> result = invokeGenerate();

        StepVerifier.create((Mono<ResponseEntity<?>>) result)
                .expectNextMatches(
                        response -> {
                            Object body = response.getBody();
                            return response.getStatusCode() == HttpStatus.INTERNAL_SERVER_ERROR
                                    && Objects.requireNonNull(getBodyStatus(body)).equals("error")
                                    && Objects.requireNonNull(getBodyMessage(body))
                                            .equals("Error generating data in catalog service")
                                    && Objects.requireNonNull(getServiceResponses(body))
                                            .containsKey("catalog")
                                    && Objects.requireNonNull(getServiceResponses(body))
                                            .get("catalog")
                                            .equals(
                                                    "Error from catalog service: Catalog service error body");
                        })
                .verifyComplete();
    }

    @Test
    void shouldHandleInventoryServiceError() {
        ResponseEntity<String> catalogResponse = ResponseEntity.ok("Test catalog data");
        WebClientResponseException inventoryException =
                new WebClientResponseException(
                        "Inventory Service Error",
                        HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "Internal Server Error from Inventory",
                        HttpHeaders.EMPTY,
                        "Inventory service error body".getBytes(),
                        null);

        when(responseSpec.toEntity(eq(String.class)))
                .thenReturn(Mono.just(catalogResponse))
                .thenReturn(Mono.error(inventoryException));

        Mono<?> result = invokeGenerate();

        StepVerifier.create((Mono<ResponseEntity<?>>) result)
                .expectNextMatches(
                        response -> {
                            Object body = response.getBody();
                            return response.getStatusCode() == HttpStatus.INTERNAL_SERVER_ERROR
                                    && Objects.requireNonNull(getBodyStatus(body)).equals("error")
                                    && Objects.requireNonNull(getBodyMessage(body))
                                            .equals("Error generating data in inventory service")
                                    && Objects.requireNonNull(getServiceResponses(body))
                                            .containsKey("inventory")
                                    && Objects.requireNonNull(getServiceResponses(body))
                                            .get("inventory")
                                            .equals(
                                                    "Error from inventory service: Inventory service error body")
                                    && Objects.requireNonNull(getServiceResponses(body))
                                            .get("catalog")
                                            .equals("Test catalog data");
                        })
                .verifyComplete();
    }

    @Test
    void shouldHandleServiceTimeoutDirectlyFromCallMicroservice() {
        when(responseSpec.toEntity(eq(String.class)))
                .thenReturn(
                        Mono.error(new java.util.concurrent.TimeoutException("Simulated timeout")));

        Mono<?> result = invokeGenerate();

        StepVerifier.create((Mono<ResponseEntity<?>>) result)
                .expectNextMatches(
                        response -> {
                            Object body = response.getBody();
                            return response.getStatusCode() == HttpStatus.REQUEST_TIMEOUT
                                    && Objects.requireNonNull(getBodyStatus(body)).equals("error")
                                    && Objects.requireNonNull(getBodyMessage(body))
                                            .equals("Error generating data in catalog service")
                                    && Objects.requireNonNull(getServiceResponses(body))
                                            .get("catalog")
                                            .equals("Timeout occurred");
                        })
                .verifyComplete();
    }

    @Test
    void shouldHandleServiceTimeoutWrappedInWebClientResponseException() {
        WebClientResponseException timeoutException =
                new WebClientResponseException(
                        HttpStatus.REQUEST_TIMEOUT.value(),
                        "Request Timeout",
                        HttpHeaders.EMPTY,
                        "Timeout".getBytes(),
                        null);

        when(responseSpec.toEntity(eq(String.class))).thenReturn(Mono.error(timeoutException));

        Mono<?> result = invokeGenerate();

        StepVerifier.create((Mono<ResponseEntity<?>>) result)
                .expectNextMatches(
                        response -> {
                            Object body = response.getBody();
                            return response.getStatusCode() == HttpStatus.REQUEST_TIMEOUT
                                    && Objects.requireNonNull(getBodyStatus(body)).equals("error")
                                    && Objects.requireNonNull(getBodyMessage(body))
                                            .equals("Error generating data in catalog service")
                                    && Objects.requireNonNull(getServiceResponses(body))
                                            .get("catalog")
                                            .equals("Error from catalog service: Timeout");
                        })
                .verifyComplete();
    }

    @Test
    void shouldHandleServiceUnavailable() {
        WebClientResponseException serviceUnavailableException =
                new WebClientResponseException(
                        HttpStatus.SERVICE_UNAVAILABLE.value(),
                        "Service Unavailable",
                        HttpHeaders.EMPTY,
                        "Service is down".getBytes(),
                        null);

        when(responseSpec.toEntity(eq(String.class)))
                .thenReturn(Mono.error(serviceUnavailableException));

        Mono<?> result = invokeGenerate();

        StepVerifier.create((Mono<ResponseEntity<?>>) result)
                .expectNextMatches(
                        response -> {
                            Object body = response.getBody();
                            return response.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE
                                    && Objects.requireNonNull(getBodyStatus(body)).equals("error")
                                    && Objects.requireNonNull(getBodyMessage(body))
                                            .equals("Error generating data in catalog service")
                                    && Objects.requireNonNull(getServiceResponses(body))
                                            .get("catalog")
                                            .equals("Service temporarily unavailable");
                        })
                .verifyComplete();
    }

    @Test
    void shouldFailAfterMaxRetriesOnServiceUnavailable() {
        WebClientResponseException serviceUnavailable =
                new WebClientResponseException(
                        HttpStatus.SERVICE_UNAVAILABLE.value(),
                        "Service Unavailable",
                        HttpHeaders.EMPTY,
                        "unavailable".getBytes(),
                        null);

        when(responseSpec.toEntity(eq(String.class)))
                .thenReturn(Mono.error(serviceUnavailable))
                .thenReturn(Mono.error(serviceUnavailable))
                .thenReturn(Mono.error(serviceUnavailable))
                .thenReturn(Mono.error(serviceUnavailable));

        Mono<?> result = invokeGenerate();

        StepVerifier.create((Mono<ResponseEntity<?>>) result)
                .expectNextMatches(
                        response -> {
                            Object body = response.getBody();
                            return response.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE
                                    && Objects.requireNonNull(getBodyStatus(body)).equals("error")
                                    && Objects.requireNonNull(getBodyMessage(body))
                                            .equals("Error generating data in catalog service")
                                    && Objects.requireNonNull(getServiceResponses(body))
                                            .get("catalog")
                                            .equals("Service temporarily unavailable");
                        })
                .verifyComplete();
    }

    @Test
    void shouldHandleCatalogReturningNonOkStatusDirectly() {
        ResponseEntity<String> catalogErrorResponse =
                ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Catalog bad request data");

        when(responseSpec.toEntity(eq(String.class))).thenReturn(Mono.just(catalogErrorResponse));

        Mono<?> result = invokeGenerate();

        StepVerifier.create((Mono<ResponseEntity<?>>) result)
                .expectNextMatches(
                        response -> {
                            Object body = response.getBody();
                            return response.getStatusCode() == HttpStatus.BAD_REQUEST
                                    && Objects.requireNonNull(getBodyStatus(body)).equals("error")
                                    && Objects.requireNonNull(getBodyMessage(body))
                                            .contains("Error generating data in catalog service")
                                    && Objects.requireNonNull(getServiceResponses(body))
                                            .get("catalog")
                                            .equals("Catalog bad request data");
                        })
                .verifyComplete();
    }

    @Test
    void shouldHandleInventoryReturningNonOkStatusDirectly() {
        ResponseEntity<String> catalogSuccessResponse = ResponseEntity.ok("Catalog data");
        ResponseEntity<String> inventoryErrorResponse =
                ResponseEntity.status(HttpStatus.NOT_FOUND).body("Inventory not found data");

        when(responseSpec.toEntity(eq(String.class)))
                .thenReturn(Mono.just(catalogSuccessResponse))
                .thenReturn(Mono.just(inventoryErrorResponse));

        Mono<?> result = invokeGenerate();

        StepVerifier.create((Mono<ResponseEntity<?>>) result)
                .expectNextMatches(
                        response -> {
                            Object body = response.getBody();
                            return response.getStatusCode() == HttpStatus.NOT_FOUND
                                    && Objects.requireNonNull(getBodyStatus(body)).equals("error")
                                    && Objects.requireNonNull(getBodyMessage(body))
                                            .contains("Error generating data in inventory service")
                                    && Objects.requireNonNull(getServiceResponses(body))
                                            .get("catalog")
                                            .equals("Catalog data")
                                    && Objects.requireNonNull(getServiceResponses(body))
                                            .get("inventory")
                                            .equals("Inventory not found data");
                        })
                .verifyComplete();
    }

    @Test
    void shouldHandleGenericExceptionDuringCatalogCall() {
        RuntimeException genericError = new RuntimeException("Generic catalog failure message");
        when(responseSpec.toEntity(eq(String.class))).thenReturn(Mono.error(genericError));

        Mono<?> result = invokeGenerate();

        StepVerifier.create((Mono<ResponseEntity<?>>) result)
                .expectNextMatches(
                        response -> {
                            Object body = response.getBody();
                            return response.getStatusCode() == HttpStatus.INTERNAL_SERVER_ERROR
                                    && Objects.requireNonNull(getBodyStatus(body)).equals("error")
                                    && Objects.requireNonNull(getBodyMessage(body))
                                            .contains("Error generating data in catalog service")
                                    && Objects.requireNonNull(getServiceResponses(body))
                                            .get("catalog")
                                            .equals(
                                                    "Unexpected error calling catalog service: Generic catalog failure message");
                        })
                .verifyComplete();
    }

    @Test
    void shouldHandleGenericExceptionDuringInventoryCall() {
        ResponseEntity<String> catalogResponse = ResponseEntity.ok("Test catalog data");
        RuntimeException genericError = new RuntimeException("Generic inventory failure message");

        when(responseSpec.toEntity(eq(String.class)))
                .thenReturn(Mono.just(catalogResponse))
                .thenReturn(Mono.error(genericError));

        Mono<?> result = invokeGenerate();

        StepVerifier.create((Mono<ResponseEntity<?>>) result)
                .expectNextMatches(
                        response -> {
                            Object body = response.getBody();
                            return response.getStatusCode() == HttpStatus.INTERNAL_SERVER_ERROR
                                    && Objects.requireNonNull(getBodyStatus(body)).equals("error")
                                    && Objects.requireNonNull(getBodyMessage(body))
                                            .contains("Error generating data in inventory service")
                                    && Objects.requireNonNull(getServiceResponses(body))
                                            .get("inventory")
                                            .equals(
                                                    "Unexpected error calling inventory service: Generic inventory failure message")
                                    && Objects.requireNonNull(getServiceResponses(body))
                                            .get("catalog")
                                            .equals("Test catalog data");
                        })
                .verifyComplete();
    }

    @Test
    void shouldHandleWebClientResponseExceptionWithEmptyBody() {
        WebClientResponseException webClientResponseExceptionWithEmptyBody =
                new WebClientResponseException(
                        HttpStatus.BAD_GATEWAY.value(),
                        "Bad Gateway",
                        HttpHeaders.EMPTY,
                        new byte[0],
                        null);

        when(responseSpec.toEntity(eq(String.class)))
                .thenReturn(Mono.error(webClientResponseExceptionWithEmptyBody));

        Mono<?> result = invokeGenerate();

        StepVerifier.create((Mono<ResponseEntity<?>>) result)
                .expectNextMatches(
                        response -> {
                            Object body = response.getBody();
                            return response.getStatusCode() == HttpStatus.BAD_GATEWAY
                                    && Objects.requireNonNull(getBodyStatus(body)).equals("error")
                                    && Objects.requireNonNull(getBodyMessage(body))
                                            .equals("Error generating data in catalog service")
                                    && Objects.requireNonNull(getServiceResponses(body))
                                            .get("catalog")
                                            .equals("Error from catalog service: 502 Bad Gateway");
                        })
                .verifyComplete();
    }

    @Test
    void shouldHandleWebClientResponseExceptionWithUnknownStatus() {
        WebClientResponseException errorWithUnknownStatus =
                new WebClientResponseException(
                        999,
                        "Unknown Error",
                        HttpHeaders.EMPTY,
                        "some error body".getBytes(),
                        null);

        when(responseSpec.toEntity(eq(String.class)))
                .thenReturn(Mono.error(errorWithUnknownStatus));

        Mono<?> result = invokeGenerate();

        StepVerifier.create((Mono<ResponseEntity<?>>) result)
                .expectNextMatches(
                        response -> {
                            Object body = response.getBody();
                            return response.getStatusCode() == HttpStatus.INTERNAL_SERVER_ERROR
                                    && Objects.requireNonNull(getBodyStatus(body)).equals("error")
                                    && Objects.requireNonNull(getBodyMessage(body))
                                            .contains("Error generating data in catalog service")
                                    && Objects.requireNonNull(getServiceResponses(body))
                                            .get("catalog")
                                            .equals("Error from catalog service: some error body");
                        })
                .verifyComplete();
    }
}
