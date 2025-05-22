/***
<p>
    Licensed under MIT License Copyright (c) 2025 Raja Kolli.
</p>
***/

package com.example.api.gateway.web.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.example.api.gateway.model.GenerationResponse;
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
class GenerateControllerUnitTest {

    @Mock private WebClient.Builder webClientBuilder;
    @Mock private WebClient webClient;
    @Mock private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;
    @Mock private WebClient.RequestHeadersSpec requestHeadersSpec;
    @Mock private WebClient.ResponseSpec responseSpec;

    private GenerateController controller;

    @BeforeEach
    void setup() {
        when(webClientBuilder.build()).thenReturn(webClient);

        // Common mocking for webClient.get().uri(...).retrieve()
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);

        // Use Duration.ZERO for tests to avoid unnecessary delays
        controller = new GenerateController(webClientBuilder, Duration.ZERO);
    }

    @Test
    void shouldGenerateDataWhenBothServicesSucceed() {
        // Given
        ResponseEntity<String> catalogResponse = ResponseEntity.ok("Test catalog data");
        ResponseEntity<String> inventoryResponse = ResponseEntity.ok("Test inventory data");

        when(responseSpec.toEntity(eq(String.class)))
                .thenReturn(Mono.just(catalogResponse))
                .thenReturn(Mono.just(inventoryResponse));

        // When
        Mono<ResponseEntity<GenerationResponse>> result = controller.generate();

        // Then
        StepVerifier.create(result)
                .expectNextMatches(
                        response -> {
                            GenerationResponse body = response.getBody();
                            return response.getStatusCode() == HttpStatus.OK
                                    && Objects.requireNonNull(body).status().equals("success")
                                    && Objects.requireNonNull(body)
                                            .message()
                                            .contains("Generation process completed successfully")
                                    && Objects.requireNonNull(body)
                                            .serviceResponses()
                                            .get("catalog")
                                            .equals("Test catalog data")
                                    && Objects.requireNonNull(body)
                                            .serviceResponses()
                                            .get("inventory")
                                            .equals("Test inventory data");
                        })
                .verifyComplete();
    }

    @Test
    void shouldHandleCatalogServiceError() {
        // Given
        WebClientResponseException catalogException =
                new WebClientResponseException(
                        "Catalog Service Error",
                        HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "Internal Server Error from Catalog",
                        HttpHeaders.EMPTY,
                        "Catalog service error body".getBytes(),
                        null);

        when(responseSpec.toEntity(eq(String.class))).thenReturn(Mono.error(catalogException));

        // When
        Mono<ResponseEntity<GenerationResponse>> result = controller.generate();

        // Then
        StepVerifier.create(result)
                .expectNextMatches(
                        response -> {
                            GenerationResponse body = response.getBody();
                            return response.getStatusCode() == HttpStatus.INTERNAL_SERVER_ERROR
                                    && Objects.requireNonNull(body).status().equals("error")
                                    // VALIDATION: Main message should indicate the stage of failure
                                    && Objects.requireNonNull(body)
                                            .message()
                                            .equals("Error generating data in catalog service")
                                    && Objects.requireNonNull(body)
                                            .serviceResponses()
                                            .containsKey("catalog")
                                    // VALIDATION: Specific error from callMicroservice is in
                                    // serviceResponses
                                    && Objects.requireNonNull(body)
                                            .serviceResponses()
                                            .get("catalog")
                                            .equals(
                                                    "Error from catalog service: Catalog service error body");
                        })
                .verifyComplete();
    }

    @Test
    void shouldHandleInventoryServiceError() {
        // Given
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

        // When
        Mono<ResponseEntity<GenerationResponse>> result = controller.generate();

        // Then
        StepVerifier.create(result)
                .expectNextMatches(
                        response -> {
                            GenerationResponse body = response.getBody();
                            return response.getStatusCode() == HttpStatus.INTERNAL_SERVER_ERROR
                                    && Objects.requireNonNull(body).status().equals("error")
                                    // VALIDATION: Main message should indicate the stage of failure
                                    && Objects.requireNonNull(body)
                                            .message()
                                            .equals("Error generating data in inventory service")
                                    && Objects.requireNonNull(body)
                                            .serviceResponses()
                                            .containsKey("inventory")
                                    // VALIDATION: Specific error from callMicroservice is in
                                    // serviceResponses
                                    && Objects.requireNonNull(body)
                                            .serviceResponses()
                                            .get("inventory")
                                            .equals(
                                                    "Error from inventory service: Inventory service error body")
                                    && Objects.requireNonNull(body)
                                            .serviceResponses()
                                            .get("catalog")
                                            .equals("Test catalog data");
                        })
                .verifyComplete();
    }

    @Test
    void shouldHandleServiceTimeoutDirectlyFromCallMicroservice() {
        // Simulates timeout within callMicroservice's onErrorResume for the first call (catalog)
        when(responseSpec.toEntity(eq(String.class)))
                .thenReturn(
                        Mono.error(new java.util.concurrent.TimeoutException("Simulated timeout")));

        // When
        Mono<ResponseEntity<GenerationResponse>> result = controller.generate();

        // Then
        StepVerifier.create(result)
                .expectNextMatches(
                        response -> {
                            GenerationResponse body = response.getBody();
                            return response.getStatusCode() == HttpStatus.REQUEST_TIMEOUT
                                    && Objects.requireNonNull(body).status().equals("error")
                                    // VALIDATION: Main message should indicate the stage of failure
                                    && Objects.requireNonNull(body)
                                            .message()
                                            .equals("Error generating data in catalog service")
                                    && Objects.requireNonNull(body)
                                            .serviceResponses()
                                            .get("catalog")
                                            // VALIDATION: Specific error from callMicroservice
                                            .equals("Timeout occurred");
                        })
                .verifyComplete();
    }

    @Test
    void shouldHandleServiceTimeoutWrappedInWebClientResponseException() {
        // Simulates a scenario where WebClient might wrap a timeout
        WebClientResponseException timeoutException =
                new WebClientResponseException(
                        HttpStatus.REQUEST_TIMEOUT.value(), // Status code
                        "Request Timeout", // Raw message in WCE
                        HttpHeaders.EMPTY,
                        "Timeout".getBytes(), // Body of WCE
                        null);

        when(responseSpec.toEntity(eq(String.class))).thenReturn(Mono.error(timeoutException));

        // When
        Mono<ResponseEntity<GenerationResponse>> result = controller.generate();

        // Then
        StepVerifier.create(result)
                .expectNextMatches(
                        response -> {
                            GenerationResponse body = response.getBody();
                            return response.getStatusCode() == HttpStatus.REQUEST_TIMEOUT
                                    && Objects.requireNonNull(body).status().equals("error")
                                    // VALIDATION: Main message should indicate the stage of failure
                                    && Objects.requireNonNull(body)
                                            .message()
                                            .equals("Error generating data in catalog service")
                                    && Objects.requireNonNull(body)
                                            .serviceResponses()
                                            .get("catalog")
                                            // VALIDATION: Specific error from callMicroservice
                                            // (uses WCE body)
                                            .equals("Error from catalog service: Timeout");
                        })
                .verifyComplete();
    }

    @Test
    void shouldHandleServiceUnavailable() {
        // Given
        WebClientResponseException serviceUnavailableException =
                new WebClientResponseException(
                        HttpStatus.SERVICE_UNAVAILABLE.value(),
                        "Service Unavailable",
                        HttpHeaders.EMPTY,
                        "Service is down".getBytes(),
                        null);

        when(responseSpec.toEntity(eq(String.class)))
                .thenReturn(Mono.error(serviceUnavailableException));

        // When
        Mono<ResponseEntity<GenerationResponse>> result = controller.generate();

        // Then
        StepVerifier.create(result)
                .expectNextMatches(
                        response -> {
                            GenerationResponse body = response.getBody();
                            return response.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE
                                    && Objects.requireNonNull(body).status().equals("error")
                                    // VALIDATION: Main message should indicate the stage of failure
                                    && Objects.requireNonNull(body)
                                            .message()
                                            .equals("Error generating data in catalog service")
                                    && Objects.requireNonNull(body)
                                            .serviceResponses()
                                            .get("catalog")
                                            // VALIDATION: Specific message for 503 from
                                            // callMicroservice
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
                .thenReturn(Mono.error(serviceUnavailable)) // Attempt 1
                .thenReturn(Mono.error(serviceUnavailable)) // Attempt 2
                .thenReturn(Mono.error(serviceUnavailable)) // Attempt 3
                .thenReturn(
                        Mono.error(
                                serviceUnavailable)); // Attempt 4 (this error will be processed by
        // callMicroservice's onErrorResume)

        Mono<ResponseEntity<GenerationResponse>> result = controller.generate();

        StepVerifier.create(result)
                .expectNextMatches(
                        response -> {
                            GenerationResponse body = response.getBody();
                            // After retries are exhausted, callMicroservice's onErrorResume for
                            // SERVICE_UNAVAILABLE kicks in.
                            // This ServiceResult then goes to generate()'s flatMap, leading to this
                            // response.
                            return response.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE
                                    && Objects.requireNonNull(body).status().equals("error")
                                    && Objects.requireNonNull(body)
                                            .message()
                                            .equals("Error generating data in catalog service")
                                    && Objects.requireNonNull(body)
                                            .serviceResponses()
                                            .get("catalog")
                                            .equals("Service temporarily unavailable");
                        })
                .verifyComplete();
    }

    @Test
    void shouldHandleCatalogReturningNonOkStatusDirectly() {
        // Given
        // This simulates the catalog service itself returning a 400, not a
        // WebClientResponseException
        ResponseEntity<String> catalogErrorResponse =
                ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Catalog bad request data");

        when(responseSpec.toEntity(eq(String.class)))
                .thenReturn(Mono.just(catalogErrorResponse)); // Catalog returns 400

        // When
        Mono<ResponseEntity<GenerationResponse>> result = controller.generate();

        // Then
        StepVerifier.create(result)
                .expectNextMatches(
                        response -> {
                            GenerationResponse body = response.getBody();
                            return response.getStatusCode() == HttpStatus.BAD_REQUEST
                                    && Objects.requireNonNull(body).status().equals("error")
                                    && Objects.requireNonNull(body)
                                            .message()
                                            .contains("Error generating data in catalog service")
                                    && Objects.requireNonNull(body)
                                            .serviceResponses()
                                            .get("catalog")
                                            .equals("Catalog bad request data");
                        })
                .verifyComplete();
    }

    @Test
    void shouldHandleInventoryReturningNonOkStatusDirectly() {
        // Given
        ResponseEntity<String> catalogSuccessResponse = ResponseEntity.ok("Catalog data");
        ResponseEntity<String> inventoryErrorResponse =
                ResponseEntity.status(HttpStatus.NOT_FOUND).body("Inventory not found data");

        when(responseSpec.toEntity(eq(String.class)))
                .thenReturn(Mono.just(catalogSuccessResponse)) // Catalog is OK
                .thenReturn(Mono.just(inventoryErrorResponse)); // Inventory returns 404

        // When
        Mono<ResponseEntity<GenerationResponse>> result = controller.generate();

        // Then
        StepVerifier.create(result)
                .expectNextMatches(
                        response -> {
                            GenerationResponse body = response.getBody();
                            return response.getStatusCode() == HttpStatus.NOT_FOUND
                                    && Objects.requireNonNull(body).status().equals("error")
                                    && Objects.requireNonNull(body)
                                            .message()
                                            .contains("Error generating data in inventory service")
                                    && Objects.requireNonNull(body)
                                            .serviceResponses()
                                            .get("catalog")
                                            .equals("Catalog data")
                                    && Objects.requireNonNull(body)
                                            .serviceResponses()
                                            .get("inventory")
                                            .equals("Inventory not found data");
                        })
                .verifyComplete();
    }

    @Test
    void shouldHandleGenericExceptionDuringCatalogCall() {
        // Given
        RuntimeException genericError = new RuntimeException("Generic catalog failure message");
        when(responseSpec.toEntity(eq(String.class))).thenReturn(Mono.error(genericError));

        // When
        Mono<ResponseEntity<GenerationResponse>> result = controller.generate();

        // Then
        StepVerifier.create(result)
                .expectNextMatches(
                        response -> {
                            GenerationResponse body = response.getBody();
                            return response.getStatusCode() == HttpStatus.INTERNAL_SERVER_ERROR
                                    && Objects.requireNonNull(body).status().equals("error")
                                    && Objects.requireNonNull(body)
                                            .message()
                                            .contains("Error generating data in catalog service")
                                    && // Corrected
                                    Objects.requireNonNull(body)
                                            .serviceResponses()
                                            .get("catalog")
                                            .equals(
                                                    "Unexpected error calling catalog service: Generic catalog failure message"); // Corrected
                        })
                .verifyComplete();
    }

    @Test
    void shouldHandleGenericExceptionDuringInventoryCall() {
        // Given
        ResponseEntity<String> catalogResponse = ResponseEntity.ok("Test catalog data");
        RuntimeException genericError = new RuntimeException("Generic inventory failure message");

        when(responseSpec.toEntity(eq(String.class)))
                .thenReturn(Mono.just(catalogResponse))
                .thenReturn(Mono.error(genericError));

        // When
        Mono<ResponseEntity<GenerationResponse>> result = controller.generate();

        // Then
        StepVerifier.create(result)
                .expectNextMatches(
                        response -> {
                            GenerationResponse body = response.getBody();
                            return response.getStatusCode() == HttpStatus.INTERNAL_SERVER_ERROR
                                    && Objects.requireNonNull(body).status().equals("error")
                                    && Objects.requireNonNull(body)
                                            .message()
                                            .contains("Error generating data in inventory service")
                                    && // Corrected
                                    Objects.requireNonNull(body)
                                            .serviceResponses()
                                            .get("inventory")
                                            .equals(
                                                    "Unexpected error calling inventory service: Generic inventory failure message")
                                    && // Corrected
                                    Objects.requireNonNull(body)
                                            .serviceResponses()
                                            .get("catalog")
                                            .equals("Test catalog data");
                        })
                .verifyComplete();
    }

    @Test
    void shouldHandleWebClientResponseExceptionWithEmptyBody() {
        // Given
        WebClientResponseException webClientResponseExceptionWithEmptyBody =
                new WebClientResponseException(
                        HttpStatus.BAD_GATEWAY.value(), // 502
                        "Bad Gateway", // status text
                        HttpHeaders.EMPTY,
                        new byte[0], // Empty body
                        null);

        when(responseSpec.toEntity(eq(String.class)))
                .thenReturn(Mono.error(webClientResponseExceptionWithEmptyBody));

        // When
        Mono<ResponseEntity<GenerationResponse>> result = controller.generate();

        // Then
        StepVerifier.create(result)
                .expectNextMatches(
                        response -> {
                            GenerationResponse body = response.getBody();
                            return response.getStatusCode() == HttpStatus.BAD_GATEWAY
                                    && Objects.requireNonNull(body).status().equals("error")
                                    && Objects.requireNonNull(body)
                                            .message()
                                            .equals("Error generating data in catalog service")
                                    && Objects.requireNonNull(body)
                                            .serviceResponses()
                                            .get("catalog")
                                            .equals("Error from catalog service: 502 Bad Gateway");
                        })
                .verifyComplete();
    }

    @Test
    void shouldHandleWebClientResponseExceptionWithUnknownStatus() {
        WebClientResponseException errorWithUnknownStatus =
                new WebClientResponseException(
                        999, // Unknown status
                        "Unknown Error", // This is wce.getMessage()
                        HttpHeaders.EMPTY,
                        "some error body".getBytes(),
                        null);

        when(responseSpec.toEntity(eq(String.class)))
                .thenReturn(Mono.error(errorWithUnknownStatus));

        Mono<ResponseEntity<GenerationResponse>> result = controller.generate();

        StepVerifier.create(result)
                .expectNextMatches(
                        response -> {
                            GenerationResponse body = response.getBody();
                            // HttpStatus.resolve(999) is null, so callMicroservice defaults to
                            // INTERNAL_SERVER_ERROR for ServiceResult status
                            return response.getStatusCode() == HttpStatus.INTERNAL_SERVER_ERROR
                                    && Objects.requireNonNull(body).status().equals("error")
                                    && Objects.requireNonNull(body)
                                            .message()
                                            .contains("Error generating data in catalog service")
                                    && // Corrected
                                    // callMicroservice uses WCE body if not empty
                                    Objects.requireNonNull(body)
                                            .serviceResponses()
                                            .get("catalog")
                                            .equals(
                                                    "Error from catalog service: some error body"); // Corrected
                        })
                .verifyComplete();
    }
}
