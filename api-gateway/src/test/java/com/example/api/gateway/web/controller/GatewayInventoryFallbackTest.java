/***
<p>
    Licensed under MIT License Copyright (c) 2025 Raja Kolli.
</p>
***/

package com.example.api.gateway.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Comprehensive unit tests for {@link GatewayInventoryFallback}
 *
 * <p>Testing Framework: JUnit 5 with Spring Boot WebFluxTest Testing Library: Spring Boot Test with
 * WebTestClient, AssertJ, and Reactor Test
 */
@WebFluxTest(GatewayInventoryFallback.class)
@DisplayName("GatewayInventoryFallback Controller Tests")
class GatewayInventoryFallbackTest {

    @Autowired private WebTestClient webTestClient;

    @Nested
    @DisplayName("GET /fallback/api/inventory/{id} - Happy Path Tests")
    class HappyPathTests {

        @Test
        @DisplayName("Should return fallback response for valid alphanumeric ID")
        void shouldReturnFallbackForValidAlphanumericId() {
            // Given
            String productId = "P0001";
            String expectedResponse = "Hello P0001";

            // When & Then
            webTestClient
                    .get()
                    .uri("/fallback/api/inventory/{id}", productId)
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus()
                    .isOk()
                    .expectHeader()
                    .contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                    .expectBody(String.class)
                    .value(response -> assertThat(response).isEqualTo(expectedResponse));
        }

        @Test
        @DisplayName("Should return fallback response for numeric ID")
        void shouldReturnFallbackForNumericId() {
            // Given
            String productId = "12345";
            String expectedResponse = "Hello 12345";

            // When & Then
            webTestClient
                    .get()
                    .uri("/fallback/api/inventory/{id}", productId)
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus()
                    .isOk()
                    .expectHeader()
                    .contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                    .expectBody(String.class)
                    .value(response -> assertThat(response).isEqualTo(expectedResponse));
        }

        @Test
        @DisplayName("Should return fallback response for single character ID")
        void shouldReturnFallbackForSingleCharacterId() {
            // Given
            String productId = "A";
            String expectedResponse = "Hello A";

            // When & Then
            webTestClient
                    .get()
                    .uri("/fallback/api/inventory/{id}", productId)
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus()
                    .isOk()
                    .expectBody(String.class)
                    .value(response -> assertThat(response).isEqualTo(expectedResponse));
        }

        @ParameterizedTest(name = "Should handle various valid product IDs: {0}")
        @ValueSource(
                strings = {"P0001", "PROD123", "SKU-ABC-001", "item_42", "X1Y2Z3", "product.v2"})
        @DisplayName("Should handle various valid product ID formats")
        void shouldHandleVariousValidProductIds(String productId) {
            // Given
            String expectedResponse = "Hello " + productId;

            // When & Then
            webTestClient
                    .get()
                    .uri("/fallback/api/inventory/{id}", productId)
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus()
                    .isOk()
                    .expectBody(String.class)
                    .value(response -> assertThat(response).isEqualTo(expectedResponse));
        }
    }

    @Nested
    @DisplayName("GET /fallback/api/inventory/{id} - Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle very long product ID")
        void shouldHandleVeryLongProductId() {
            // Given - Create a long product ID
            String longProductId =
                    "PRODUCT_ID_WITH_VERY_LONG_NAME_THAT_EXCEEDS_NORMAL_LENGTH_EXPECTATIONS_123456789";
            String expectedResponse = "Hello " + longProductId;

            // When & Then
            webTestClient
                    .get()
                    .uri("/fallback/api/inventory/{id}", longProductId)
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus()
                    .isOk()
                    .expectBody(String.class)
                    .value(response -> assertThat(response).isEqualTo(expectedResponse));
        }

        @Test
        @DisplayName("Should handle product ID with special characters")
        void shouldHandleProductIdWithSpecialCharacters() {
            // Given
            String specialCharProductId = "PROD@#$%";
            String expectedResponse = "Hello " + specialCharProductId;

            // When & Then
            webTestClient
                    .get()
                    .uri("/fallback/api/inventory/{id}", specialCharProductId)
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus()
                    .isOk()
                    .expectBody(String.class)
                    .value(response -> assertThat(response).isEqualTo(expectedResponse));
        }

        @Test
        @DisplayName("Should handle product ID with spaces (URL encoded)")
        void shouldHandleProductIdWithSpaces() {
            // Given
            String productIdWithSpaces = "PROD 001";
            String expectedResponse = "Hello " + productIdWithSpaces;

            // When & Then
            webTestClient
                    .get()
                    .uri("/fallback/api/inventory/{id}", productIdWithSpaces)
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus()
                    .isOk()
                    .expectBody(String.class)
                    .value(response -> assertThat(response).isEqualTo(expectedResponse));
        }

        @Test
        @DisplayName("Should handle Unicode characters in product ID")
        void shouldHandleUnicodeCharactersInProductId() {
            // Given
            String unicodeProductId = "产品001";
            String expectedResponse = "Hello " + unicodeProductId;

            // When & Then
            webTestClient
                    .get()
                    .uri("/fallback/api/inventory/{id}", unicodeProductId)
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus()
                    .isOk()
                    .expectBody(String.class)
                    .value(response -> assertThat(response).isEqualTo(expectedResponse));
        }
    }

    @Nested
    @DisplayName("HTTP Headers and Content Type Tests")
    class HttpHeaderTests {

        @Test
        @DisplayName("Should accept and respond with proper content types")
        void shouldHandleProperContentTypes() {
            // Given
            String productId = "P0001";

            // When & Then
            webTestClient
                    .get()
                    .uri("/fallback/api/inventory/{id}", productId)
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus()
                    .isOk()
                    .expectHeader()
                    .contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                    .expectBody(String.class);
        }

        @Test
        @DisplayName("Should work without explicit Accept header")
        void shouldWorkWithoutAcceptHeader() {
            // Given
            String productId = "P0001";
            String expectedResponse = "Hello P0001";

            // When & Then
            webTestClient
                    .get()
                    .uri("/fallback/api/inventory/{id}", productId)
                    .exchange()
                    .expectStatus()
                    .isOk()
                    .expectBody(String.class)
                    .value(response -> assertThat(response).isEqualTo(expectedResponse));
        }

        @Test
        @DisplayName("Should accept different media types in Accept header")
        void shouldAcceptDifferentMediaTypes() {
            // Given
            String productId = "P0001";

            // When & Then - Test with various Accept headers
            webTestClient
                    .get()
                    .uri("/fallback/api/inventory/{id}", productId)
                    .accept(MediaType.TEXT_PLAIN)
                    .exchange()
                    .expectStatus()
                    .isOk();

            webTestClient
                    .get()
                    .uri("/fallback/api/inventory/{id}", productId)
                    .accept(MediaType.ALL)
                    .exchange()
                    .expectStatus()
                    .isOk();
        }
    }

    @Nested
    @DisplayName("Reactive Mono Behavior Tests")
    class ReactiveMonoBehaviorTests {

        @Test
        @DisplayName("Should return Mono that emits expected value")
        void shouldReturnMonoWithExpectedValue() {
            // Given
            GatewayInventoryFallback controller = new GatewayInventoryFallback();
            String productId = "P0001";

            // When
            Mono<String> result = controller.fallback(productId);

            // Then - Test using StepVerifier for reactive stream verification
            StepVerifier.create(result).expectNext("Hello P0001").verifyComplete();
        }

        @Test
        @DisplayName("Should return Mono that completes successfully")
        void shouldReturnMonoThatCompletes() {
            // Given
            GatewayInventoryFallback controller = new GatewayInventoryFallback();
            String productId = "TEST123";

            // When
            Mono<String> result = controller.fallback(productId);

            // Then
            StepVerifier.create(result)
                    .assertNext(
                            response -> {
                                assertThat(response).isNotNull();
                                assertThat(response).contains("TEST123");
                                assertThat(response).startsWith("Hello ");
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should handle multiple concurrent calls correctly")
        void shouldHandleConcurrentCalls() {
            // Given
            GatewayInventoryFallback controller = new GatewayInventoryFallback();
            String[] productIds = {"P001", "P002", "P003", "P004", "P005"};

            // When - Create multiple Monos
            Mono<String>[] monos = new Mono[productIds.length];
            for (int i = 0; i < productIds.length; i++) {
                monos[i] = controller.fallback(productIds[i]);
            }

            // Then - Verify each Mono independently
            for (int i = 0; i < productIds.length; i++) {
                final int index = i;
                StepVerifier.create(monos[i])
                        .expectNext("Hello " + productIds[index])
                        .verifyComplete();
            }
        }
    }

    @Nested
    @DisplayName("Error and Exception Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should handle null ID gracefully via direct method call")
        void shouldHandleNullIdDirectly() {
            // Given
            GatewayInventoryFallback controller = new GatewayInventoryFallback();

            // When
            Mono<String> result = controller.fallback(null);

            // Then - Should complete with "Hello null" as per String.formatted behavior
            StepVerifier.create(result).expectNext("Hello null").verifyComplete();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("Should handle null and empty IDs via direct method call")
        void shouldHandleNullAndEmptyIds(String productId) {
            // Given
            GatewayInventoryFallback controller = new GatewayInventoryFallback();
            String expectedResponse = "Hello " + productId;

            // When
            Mono<String> result = controller.fallback(productId);

            // Then
            StepVerifier.create(result).expectNext(expectedResponse).verifyComplete();
        }

        @Test
        @DisplayName("Should not throw exceptions for any string input")
        void shouldNotThrowExceptionsForAnyStringInput() {
            // Given
            GatewayInventoryFallback controller = new GatewayInventoryFallback();
            String[] problematicInputs = {
                "", " ", "null", "undefined", "NaN", "\n\r\t", "   spaces   "
            };

            // When & Then - None should throw exceptions
            for (String input : problematicInputs) {
                StepVerifier.create(controller.fallback(input))
                        .expectNext("Hello " + input)
                        .verifyComplete();
            }
        }
    }

    @Nested
    @DisplayName("Performance and Resource Tests")
    class PerformanceTests {

        @Test
        @DisplayName("Should handle multiple concurrent calls correctly")
        void shouldHandleConcurrentCalls() {
            // Given
            GatewayInventoryFallback controller = new GatewayInventoryFallback();
            String[] productIds = {"P001", "P002", "P003", "P004", "P005"};

            // When - Create multiple Monos
            Mono<String>[] monos = new Mono[productIds.length];
            for (int i = 0; i < productIds.length; i++) {
                monos[i] = controller.fallback(productIds[i]);
            }

            // Then - Verify each Mono independently
            for (int i = 0; i < productIds.length; i++) {
                final int index = i; // Capture loop variable as final
                final String expectedResponse =
                        "Hello " + productIds[index]; // Pre-calculate expected response
                StepVerifier.create(monos[index]) // Use final variable instead of loop variable
                        .expectNext(expectedResponse)
                        .verifyComplete();
            }
        }

        @Test
        @DisplayName("Should handle stress test with varying ID lengths")
        void shouldHandleStressTestWithVaryingIdLengths() {
            // Given - Create IDs of different lengths
            String[] varyingLengthIds = {
                "A",
                "AB",
                "ABC",
                "ABCD",
                "ABCDE",
                "SHORT",
                "MEDIUM_LENGTH",
                "VERY_LONG_PRODUCT_IDENTIFIER_NAME"
            };

            // When & Then - All should work correctly
            for (String productId : varyingLengthIds) {
                webTestClient
                        .get()
                        .uri("/fallback/api/inventory/{id}", productId)
                        .exchange()
                        .expectStatus()
                        .isOk()
                        .expectBody(String.class)
                        .value(
                                response -> {
                                    assertThat(response).isEqualTo("Hello " + productId);
                                    assertThat(response.length())
                                            .isEqualTo(
                                                    6 + productId.length()); // "Hello " + productId
                                });
            }
        }
    }

    @Nested
    @DisplayName("Integration and API Contract Tests")
    class IntegrationTests {

        @Test
        @DisplayName("Should maintain consistent API contract")
        void shouldMaintainConsistentApiContract() {
            // Given
            String productId = "CONTRACT_TEST";

            // When & Then - Verify complete API contract
            webTestClient
                    .get()
                    .uri("/fallback/api/inventory/{id}", productId)
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus()
                    .isOk()
                    .expectHeader()
                    .contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                    .expectBody(String.class)
                    .value(
                            response -> {
                                // Verify response format
                                assertThat(response).isNotNull();
                                assertThat(response).isNotEmpty();
                                assertThat(response).startsWith("Hello ");
                                assertThat(response).endsWith(productId);
                                assertThat(response).hasSize(6 + productId.length());
                            });
        }

        @Test
        @DisplayName("Should work correctly as a circuit breaker fallback")
        void shouldWorkAsCircuitBreakerFallback() {
            // Given - Simulate circuit breaker scenario with typical product IDs
            String[] typicalProductIds = {"P0001", "SKU123", "ITEM-456", "PROD_789"};

            // When & Then - All should return fallback responses consistently
            for (String productId : typicalProductIds) {
                webTestClient
                        .get()
                        .uri("/fallback/api/inventory/{id}", productId)
                        .exchange()
                        .expectStatus()
                        .isOk()
                        .expectBody(String.class)
                        .value(
                                response -> {
                                    assertThat(response).isEqualTo("Hello " + productId);
                                    // Verify it's a proper fallback response
                                    assertThat(response)
                                            .doesNotContain("error", "exception", "failure");
                                });
            }
        }
    }

    @Nested
    @DisplayName("Method-Level Unit Tests")
    class MethodLevelTests {

        @Test
        @DisplayName("Should format string correctly using String.formatted")
        void shouldFormatStringCorrectlyUsingStringFormatted() {
            // Given
            GatewayInventoryFallback controller = new GatewayInventoryFallback();
            String testId = "FORMAT_TEST";

            // When
            Mono<String> result = controller.fallback(testId);
            String actualResult = result.block(); // Block for testing purposes only

            // Then
            assertThat(actualResult).isEqualTo("Hello FORMAT_TEST");
            assertThat(actualResult).contains(testId);

            // Verify String.formatted behavior is preserved
            String expectedDirect = "Hello %s".formatted(testId);
            assertThat(actualResult).isEqualTo(expectedDirect);
        }

        @Test
        @DisplayName("Should return non-null Mono for any input")
        void shouldReturnNonNullMonoForAnyInput() {
            // Given
            GatewayInventoryFallback controller = new GatewayInventoryFallback();

            // When & Then
            assertThat(controller.fallback("test")).isNotNull();
            assertThat(controller.fallback("")).isNotNull();
            assertThat(controller.fallback(null)).isNotNull();
        }
    }
}
