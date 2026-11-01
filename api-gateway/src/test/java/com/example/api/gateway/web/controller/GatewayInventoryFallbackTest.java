/***
<p>
    Licensed under MIT License Copyright (c) 2025-2026 Raja Kolli.
</p>
***/

package com.example.api.gateway.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Comprehensive unit tests for {@link GatewayInventoryFallback}
 *
 * <p>Testing Framework: JUnit 5 with Spring Boot WebFluxTest Testing Library: Spring Boot Test with
 * WebTestClient, AssertJ, and Reactor Test
 */
@WebFluxTest(
        controllers = GatewayInventoryFallback.class,
        properties = "spring.cloud.config.enabled=false")
@DisplayName("GatewayInventoryFallback Controller Tests")
class GatewayInventoryFallbackTest {

    @Autowired private WebTestClient webTestClient;

    @Nested
    @DisplayName("GET /fallback/api/inventory/{id} - Happy Path Tests")
    class HappyPathTests {

        @Test
        @DisplayName("Should return ProblemDetail fallback response for valid alphanumeric ID")
        void shouldReturnFallbackForValidAlphanumericId() {
            String productId = "P0001";

            webTestClient
                    .get()
                    .uri("/fallback/api/inventory/{id}", productId)
                    .accept(MediaType.APPLICATION_PROBLEM_JSON, MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus()
                    .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                    .expectHeader()
                    .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                    .expectBody(ProblemDetail.class)
                    .value(
                            problem -> {
                                assertThat(problem.getStatus())
                                        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
                                assertThat(problem.getTitle())
                                        .isEqualTo("Inventory Service Unavailable");
                                assertThat(problem.getDetail())
                                        .isEqualTo(
                                                "Inventory service is currently unavailable for product P0001");
                                assertThat(problem.getType())
                                        .isEqualTo(
                                                URI.create(
                                                        "urn:problem-type:inventory-service-unavailable"));
                                assertThat(problem.getProperties())
                                        .containsEntry("productId", "P0001");
                            });
        }

        @Test
        @DisplayName("Should return ProblemDetail fallback response for numeric ID")
        void shouldReturnFallbackForNumericId() {
            String productId = "12345";

            webTestClient
                    .get()
                    .uri("/fallback/api/inventory/{id}", productId)
                    .exchange()
                    .expectStatus()
                    .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                    .expectBody(ProblemDetail.class)
                    .value(
                            problem -> {
                                assertThat(problem.getStatus())
                                        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
                                assertThat(problem.getDetail()).contains("12345");
                                assertThat(problem.getProperties())
                                        .containsEntry("productId", "12345");
                            });
        }

        @Test
        @DisplayName("Should return ProblemDetail fallback response for single character ID")
        void shouldReturnFallbackForSingleCharacterId() {
            String productId = "A";

            webTestClient
                    .get()
                    .uri("/fallback/api/inventory/{id}", productId)
                    .exchange()
                    .expectStatus()
                    .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                    .expectBody(ProblemDetail.class)
                    .value(
                            problem -> {
                                assertThat(problem.getStatus())
                                        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
                                assertThat(problem.getDetail()).contains("A");
                                assertThat(problem.getProperties()).containsEntry("productId", "A");
                            });
        }

        @ParameterizedTest(name = "Should handle various valid product IDs: {0}")
        @ValueSource(
                strings = {"P0001", "PROD123", "SKU-ABC-001", "item_42", "X1Y2Z3", "product.v2"})
        @DisplayName("Should handle various valid product ID formats")
        void shouldHandleVariousValidProductIds(String productId) {
            webTestClient
                    .get()
                    .uri("/fallback/api/inventory/{id}", productId)
                    .exchange()
                    .expectStatus()
                    .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                    .expectBody(ProblemDetail.class)
                    .value(
                            problem -> {
                                assertThat(problem.getStatus())
                                        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
                                assertThat(problem.getDetail()).contains(productId);
                                assertThat(problem.getProperties())
                                        .containsEntry("productId", productId);
                            });
        }

        @Test
        @DisplayName("Should return ProblemDetail fallback response when id is omitted")
        void shouldReturnFallbackWhenIdOmitted() {
            webTestClient
                    .get()
                    .uri("/fallback/api/inventory")
                    .exchange()
                    .expectStatus()
                    .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                    .expectBody(ProblemDetail.class)
                    .value(
                            problem -> {
                                assertThat(problem.getStatus())
                                        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
                                assertThat(problem.getTitle())
                                        .isEqualTo("Inventory Service Unavailable");
                                assertThat(problem.getDetail())
                                        .isEqualTo("Inventory service is currently unavailable");
                            });
        }
    }

    @Nested
    @DisplayName("GET /fallback/api/inventory/{id} - Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle very long product ID")
        void shouldHandleVeryLongProductId() {
            String longProductId =
                    "PRODUCT_ID_WITH_VERY_LONG_NAME_THAT_EXCEEDS_NORMAL_LENGTH_EXPECTATIONS_123456789";

            webTestClient
                    .get()
                    .uri("/fallback/api/inventory/{id}", longProductId)
                    .exchange()
                    .expectStatus()
                    .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                    .expectBody(ProblemDetail.class)
                    .value(
                            problem -> {
                                assertThat(problem.getProperties())
                                        .containsEntry("productId", longProductId);
                                assertThat(problem.getDetail()).contains(longProductId);
                            });
        }

        @Test
        @DisplayName("Should handle product ID with special characters")
        void shouldHandleProductIdWithSpecialCharacters() {
            String specialCharProductId = "PROD@#$%";

            webTestClient
                    .get()
                    .uri("/fallback/api/inventory/{id}", specialCharProductId)
                    .exchange()
                    .expectStatus()
                    .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                    .expectBody(ProblemDetail.class)
                    .value(
                            problem -> {
                                assertThat(problem.getProperties())
                                        .containsEntry("productId", specialCharProductId);
                                assertThat(problem.getDetail()).contains(specialCharProductId);
                            });
        }

        @Test
        @DisplayName("Should handle product ID with spaces (URL encoded)")
        void shouldHandleProductIdWithSpaces() {
            String productIdWithSpaces = "PROD 001";

            webTestClient
                    .get()
                    .uri("/fallback/api/inventory/{id}", productIdWithSpaces)
                    .exchange()
                    .expectStatus()
                    .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                    .expectBody(ProblemDetail.class)
                    .value(
                            problem -> {
                                assertThat(problem.getProperties())
                                        .containsEntry("productId", productIdWithSpaces);
                                assertThat(problem.getDetail()).contains(productIdWithSpaces);
                            });
        }

        @Test
        @DisplayName("Should handle Unicode characters in product ID")
        void shouldHandleUnicodeCharactersInProductId() {
            String unicodeProductId = "产品001";

            webTestClient
                    .get()
                    .uri("/fallback/api/inventory/{id}", unicodeProductId)
                    .exchange()
                    .expectStatus()
                    .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                    .expectBody(ProblemDetail.class)
                    .value(
                            problem -> {
                                assertThat(problem.getProperties())
                                        .containsEntry("productId", unicodeProductId);
                                assertThat(problem.getDetail()).contains(unicodeProductId);
                            });
        }
    }

    @Nested
    @DisplayName("HTTP Headers and Content Type Tests")
    class HttpHeaderTests {

        @Test
        @DisplayName("Should accept and respond with proper content types")
        void shouldHandleProperContentTypes() {
            String productId = "P0001";

            webTestClient
                    .get()
                    .uri("/fallback/api/inventory/{id}", productId)
                    .accept(MediaType.APPLICATION_PROBLEM_JSON)
                    .exchange()
                    .expectStatus()
                    .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                    .expectHeader()
                    .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                    .expectBody(ProblemDetail.class);
        }

        @Test
        @DisplayName("Should work without explicit Accept header")
        void shouldWorkWithoutAcceptHeader() {
            String productId = "P0001";

            webTestClient
                    .get()
                    .uri("/fallback/api/inventory/{id}", productId)
                    .exchange()
                    .expectStatus()
                    .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                    .expectBody(ProblemDetail.class)
                    .value(
                            problem ->
                                    assertThat(problem.getProperties())
                                            .containsEntry("productId", productId));
        }

        @Test
        @DisplayName("Should accept different media types in Accept header")
        void shouldAcceptDifferentMediaTypes() {
            String productId = "P0001";

            webTestClient
                    .get()
                    .uri("/fallback/api/inventory/{id}", productId)
                    .accept(MediaType.ALL)
                    .exchange()
                    .expectStatus()
                    .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    @Nested
    @DisplayName("Reactive Mono Behavior Tests")
    class ReactiveMonoBehaviorTests {

        @Test
        @DisplayName("Should return Mono that emits expected value")
        void shouldReturnMonoWithExpectedValue() {
            GatewayInventoryFallback controller = new GatewayInventoryFallback();
            String productId = "P0001";

            Mono<ProblemDetail> result = controller.fallback(productId);

            StepVerifier.create(result)
                    .assertNext(
                            problem -> {
                                assertThat(problem.getStatus())
                                        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
                                assertThat(problem.getDetail()).contains("P0001");
                                assertThat(problem.getProperties())
                                        .containsEntry("productId", "P0001");
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should return Mono that completes successfully")
        void shouldReturnMonoThatCompletes() {
            GatewayInventoryFallback controller = new GatewayInventoryFallback();
            String productId = "TEST123";

            Mono<ProblemDetail> result = controller.fallback(productId);

            StepVerifier.create(result)
                    .assertNext(
                            problem -> {
                                assertThat(problem).isNotNull();
                                assertThat(problem.getDetail()).contains("TEST123");
                                assertThat(problem.getTitle())
                                        .isEqualTo("Inventory Service Unavailable");
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should handle multiple concurrent calls correctly")
        void shouldHandleConcurrentCalls() {
            GatewayInventoryFallback controller = new GatewayInventoryFallback();
            String[] productIds = {"P001", "P002", "P003", "P004", "P005"};

            @SuppressWarnings("unchecked")
            Mono<ProblemDetail>[] monos = new Mono[productIds.length];
            for (int i = 0; i < productIds.length; i++) {
                monos[i] = controller.fallback(productIds[i]);
            }

            for (int i = 0; i < productIds.length; i++) {
                final String expectedId = productIds[i];
                StepVerifier.create(monos[i])
                        .assertNext(
                                problem ->
                                        assertThat(problem.getProperties())
                                                .containsEntry("productId", expectedId))
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
            GatewayInventoryFallback controller = new GatewayInventoryFallback();

            Mono<ProblemDetail> result = controller.fallback(null);

            StepVerifier.create(result)
                    .assertNext(
                            problem -> {
                                assertThat(problem.getStatus())
                                        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
                                assertThat(problem.getDetail())
                                        .isEqualTo("Inventory service is currently unavailable");
                                assertThat(problem.getProperties()).isNull();
                            })
                    .verifyComplete();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("Should handle null and empty IDs via direct method call")
        void shouldHandleNullAndEmptyIds(String productId) {
            GatewayInventoryFallback controller = new GatewayInventoryFallback();

            Mono<ProblemDetail> result = controller.fallback(productId);

            StepVerifier.create(result)
                    .assertNext(
                            problem -> {
                                assertThat(problem.getStatus())
                                        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
                                assertThat(problem.getDetail())
                                        .isEqualTo("Inventory service is currently unavailable");
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should not throw exceptions for any string input")
        void shouldNotThrowExceptionsForAnyStringInput() {
            GatewayInventoryFallback controller = new GatewayInventoryFallback();
            String[] problematicInputs = {
                "", " ", "null", "undefined", "NaN", "\n\r\t", "   spaces   "
            };

            for (String input : problematicInputs) {
                StepVerifier.create(controller.fallback(input))
                        .assertNext(
                                problem ->
                                        assertThat(problem.getStatus())
                                                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value()))
                        .verifyComplete();
            }
        }
    }

    @Nested
    @DisplayName("Performance and Resource Tests")
    class PerformanceTests {

        @Test
        @DisplayName("Should handle stress test with varying ID lengths")
        void shouldHandleStressTestWithVaryingIdLengths() {
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

            for (String productId : varyingLengthIds) {
                webTestClient
                        .get()
                        .uri("/fallback/api/inventory/{id}", productId)
                        .exchange()
                        .expectStatus()
                        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                        .expectBody(ProblemDetail.class)
                        .value(
                                problem -> {
                                    assertThat(problem.getStatus())
                                            .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
                                    assertThat(problem.getProperties())
                                            .containsEntry("productId", productId);
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
            String productId = "CONTRACT_TEST";

            webTestClient
                    .get()
                    .uri("/fallback/api/inventory/{id}", productId)
                    .accept(MediaType.APPLICATION_PROBLEM_JSON, MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus()
                    .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                    .expectHeader()
                    .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                    .expectBody(ProblemDetail.class)
                    .value(
                            problem -> {
                                assertThat(problem).isNotNull();
                                assertThat(problem.getStatus())
                                        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
                                assertThat(problem.getTitle())
                                        .isEqualTo("Inventory Service Unavailable");
                                assertThat(problem.getDetail())
                                        .isEqualTo(
                                                "Inventory service is currently unavailable for product CONTRACT_TEST");
                                assertThat(problem.getType())
                                        .isEqualTo(
                                                URI.create(
                                                        "urn:problem-type:inventory-service-unavailable"));
                                assertThat(problem.getProperties())
                                        .containsEntry("productId", "CONTRACT_TEST");
                            });
        }

        @Test
        @DisplayName("Should work correctly as a circuit breaker fallback")
        void shouldWorkAsCircuitBreakerFallback() {
            String[] typicalProductIds = {"P0001", "SKU123", "ITEM-456", "PROD_789"};

            for (String productId : typicalProductIds) {
                webTestClient
                        .get()
                        .uri("/fallback/api/inventory/{id}", productId)
                        .exchange()
                        .expectStatus()
                        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                        .expectBody(ProblemDetail.class)
                        .value(
                                problem -> {
                                    assertThat(problem.getStatus())
                                            .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
                                    assertThat(problem.getProperties())
                                            .containsEntry("productId", productId);
                                });
            }
        }
    }

    @Nested
    @DisplayName("Method-Level Unit Tests")
    class MethodLevelTests {

        @Test
        @DisplayName("Should return non-null Mono with ProblemDetail for any input")
        void shouldReturnNonNullMonoForAnyInput() {
            GatewayInventoryFallback controller = new GatewayInventoryFallback();

            assertThat(controller.fallback("test")).isNotNull();
            assertThat(controller.fallback("")).isNotNull();
            assertThat(controller.fallback(null)).isNotNull();
        }
    }
}
