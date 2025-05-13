/***
<p>
    Licensed under MIT License Copyright (c) 2021-2025 Raja Kolli.
</p>
***/

package com.example.catalogservice.web.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.is;

import com.example.catalogservice.common.AbstractCircuitBreakerTest;
import com.example.catalogservice.config.TestKafkaListenerConfig;
import com.example.catalogservice.entities.Product;
import com.example.catalogservice.model.request.ProductRequest;
import com.example.catalogservice.model.response.InventoryResponse;
import com.example.catalogservice.model.response.PagedResult;
import com.example.catalogservice.repositories.ProductRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import reactor.core.publisher.Mono;

class ProductControllerIT extends AbstractCircuitBreakerTest {

    @Autowired private ProductRepository productRepository;
    @Autowired private TestKafkaListenerConfig testKafkaListenerConfig;

    public static MockWebServer mockWebServer;

    private List<Product> savedProductList = new ArrayList<>();

    @BeforeAll
    static void setUpServer() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
    }

    @AfterAll
    static void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @DynamicPropertySource
    static void backendProperties(DynamicPropertyRegistry registry) {
        registry.add(
                "application.inventory-service-url", () -> mockWebServer.url("/").url().toString());
    }

    @BeforeEach
    void setUp() {

        List<Product> productList =
                List.of(
                        new Product()
                                .setProductCode("P001")
                                .setProductName("name 1")
                                .setDescription("description 1")
                                .setPrice(9.0),
                        new Product()
                                .setProductCode("P002")
                                .setProductName("name 2")
                                .setDescription("description 2")
                                .setPrice(10.0),
                        new Product()
                                .setProductCode("P003")
                                .setProductName("name 3")
                                .setDescription("description 3")
                                .setPrice(11.0));
        savedProductList =
                productRepository
                        .deleteAll()
                        .thenMany(productRepository.saveAll(productList))
                        .thenMany(productRepository.findAll())
                        .collectList()
                        .block();
    }

    @Test
    void shouldFetchAllProducts() throws JsonProcessingException {

        transitionToClosedState("getInventoryByProductCodes");

        mockBackendEndpoint(
                200, objectMapper.writeValueAsString(List.of(new InventoryResponse("P003", 0))));

        webTestClient
                .get()
                .uri("/api/catalog?pageSize=2&pageNo=1")
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .contentType(MediaType.APPLICATION_JSON)
                .expectBody(PagedResult.class)
                .consumeWith(
                        response -> {
                            PagedResult<Product> pagedResult = response.getResponseBody();
                            assertThat(pagedResult).isNotNull();
                            assertThat(pagedResult.isFirst()).isFalse();
                            assertThat(pagedResult.isLast()).isTrue();
                            assertThat(pagedResult.hasNext()).isFalse();
                            assertThat(pagedResult.hasPrevious()).isTrue();
                            assertThat(pagedResult.totalElements()).isEqualTo(3);
                            assertThat(pagedResult.pageNumber()).isEqualTo(2);
                            assertThat(pagedResult.totalPages()).isEqualTo(2);
                            assertThat(pagedResult.data()).hasSize(1);
                        });

        checkHealthStatus("getInventoryByProductCodes", CircuitBreaker.State.CLOSED);
    }

    @Test
    void shouldFetchAllProductsAsEmpty() {

        productRepository.deleteAll().block();
        webTestClient
                .get()
                .uri(
                        uriBuilder -> {
                            uriBuilder.queryParam("sortDir", "desc");
                            uriBuilder.path("/api/catalog");
                            return uriBuilder.build();
                        })
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .contentType(MediaType.APPLICATION_JSON)
                .expectBody(PagedResult.class)
                .value(
                        response ->
                                assertThat(response)
                                        .isNotNull()
                                        .satisfies(
                                                r -> {
                                                    assertThat(r.isFirst()).isTrue();
                                                    assertThat(r.isLast()).isTrue();
                                                    assertThat(r.hasNext()).isFalse();
                                                    assertThat(r.hasPrevious()).isFalse();
                                                    assertThat(r.totalElements()).isZero();
                                                    assertThat(r.pageNumber()).isEqualTo(1);
                                                    assertThat(r.totalPages()).isZero();
                                                    assertThat(r.data()).isEmpty();
                                                }));

        checkHealthStatus("getInventoryByProductCodes", CircuitBreaker.State.HALF_OPEN);
    }

    @Test
    void shouldFetchAllProductsWithCircuitBreaker() {

        transitionToOpenState("getInventoryByProductCodes");
        transitionToHalfOpenState("getInventoryByProductCodes");

        mockBackendEndpoint(
                500,
                """
                        {"message":"Product with id 100 not found"}
                        """);

        webTestClient
                .get()
                .uri("/api/catalog")
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .contentType(MediaType.APPLICATION_JSON)
                .expectBody(PagedResult.class)
                .value(
                        response ->
                                assertThat(response)
                                        .isNotNull()
                                        .satisfies(
                                                r -> {
                                                    assertThat(r.isFirst()).isTrue();
                                                    assertThat(r.isLast()).isTrue();
                                                    assertThat(r.hasNext()).isFalse();
                                                    assertThat(r.hasPrevious()).isFalse();
                                                    assertThat(r.totalElements()).isEqualTo(3);
                                                    assertThat(r.pageNumber()).isEqualTo(1);
                                                    assertThat(r.totalPages()).isEqualTo(1);
                                                    assertThat(r.data())
                                                            .hasSameSizeAs(savedProductList);
                                                }));

        // Then, As it is still failing state should not change
        checkHealthStatus("getInventoryByProductCodes", CircuitBreaker.State.HALF_OPEN);
    }

    @Test
    void shouldFindProductById() throws JsonProcessingException {

        transitionToClosedState("default");

        Product product = savedProductList.getFirst();
        Long productId = product.getId();
        mockBackendEndpoint(
                200,
                objectMapper.writeValueAsString(
                        new InventoryResponse(product.getProductCode(), 0)));
        webTestClient
                .get()
                .uri("/api/catalog/id/{id}", productId)
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.id")
                .isEqualTo(product.getId())
                .jsonPath("$.productCode")
                .isEqualTo(product.getProductCode())
                .jsonPath("$.productName")
                .isEqualTo(product.getProductName())
                .jsonPath("$.description")
                .isEqualTo(product.getDescription())
                .jsonPath("$.price")
                .isEqualTo(product.getPrice())
                .jsonPath("$.inStock")
                .isEqualTo(false);
        checkHealthStatus("default", CircuitBreaker.State.CLOSED);
    }

    @Test
    void shouldRetryOnErrorAndFetchSuccessResponse() throws JsonProcessingException {

        int requestCount = mockWebServer.getRequestCount();
        mockBackendEndpoint(
                500,
                """
                        {"message":"Product with id 100 not found"}
                    """);
        mockBackendEndpoint(
                500,
                """
                        {"message":"Product with id 100 not found"}
                    """);

        Product product = savedProductList.getFirst();
        Long productId = product.getId();
        mockBackendEndpoint(
                200,
                objectMapper.writeValueAsString(
                        new InventoryResponse(product.getProductCode(), 10)));

        webTestClient
                .get()
                .uri("/api/catalog/id/{id}", productId)
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.id")
                .isEqualTo(product.getId())
                .jsonPath("$.productCode")
                .isEqualTo(product.getProductCode())
                .jsonPath("$.productName")
                .isEqualTo(product.getProductName())
                .jsonPath("$.description")
                .isEqualTo(product.getDescription())
                .jsonPath("$.price")
                .isEqualTo(product.getPrice())
                .jsonPath("$.inStock")
                .isEqualTo(true);
        assertThat(mockWebServer.getRequestCount() - requestCount).isEqualTo(3);
        checkHealthStatus("default", CircuitBreaker.State.CLOSED);
    }

    @Test
    void shouldRetryOnErrorAndBreakCircuitResponse() throws JsonProcessingException {

        int requestCount = mockWebServer.getRequestCount();

        transitionToOpenState("default");
        transitionToHalfOpenState("default");
        mockBackendEndpoint(
                500,
                """
                        {"message":"Product with id 100 not found"}
                    """);
        mockBackendEndpoint(
                500,
                """
                        {"message":"Product with id 100 not found"}
                    """);
        mockBackendEndpoint(
                500,
                """
                        {"message":"Product with id 100 not found"}
                    """);

        Product product = savedProductList.getFirst();
        Long productId = product.getId();
        mockBackendEndpoint(
                200,
                objectMapper.writeValueAsString(
                        new InventoryResponse(product.getProductCode(), 10)));

        webTestClient
                .get()
                .uri("/api/catalog/id/{id}", productId)
                .exchange()
                .expectStatus()
                .is5xxServerError()
                .expectHeader()
                .contentType(MediaType.APPLICATION_JSON);
        assertThat(mockWebServer.getRequestCount() - requestCount).isEqualTo(3);

        await().atMost(Duration.ofMillis(10000))
                .untilAsserted(() -> checkHealthStatus("default", CircuitBreaker.State.HALF_OPEN));
    }

    @Test
    void shouldRetryAndFailAndBreakCloseTheCircuitTest() throws JsonProcessingException {

        Product product = savedProductList.getFirst();
        Long productId = product.getId();
        // key here is adding 2nd enqueue request as call is made twice
        mockBackendEndpoint(
                500,
                """
                        {"message":"Product with id 100 not found"}
                    """);
        mockBackendEndpoint(
                500,
                """
                        {"message":"Product with id 100 not found"}
                    """);
        mockBackendEndpoint(500, "ERROR");
        mockBackendEndpoint(500, "ERROR");
        mockBackendEndpoint(
                200,
                objectMapper.writeValueAsString(
                        new InventoryResponse(product.getProductCode(), 10)));
        webTestClient
                .get()
                .uri("/api/catalog/id/{id}", productId)
                .exchange()
                .expectStatus()
                .is5xxServerError()
                .expectHeader()
                .contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo(500)
                .jsonPath("$.path")
                .isEqualTo("/api/catalog/id/" + productId);

        checkHealthStatus("default", CircuitBreaker.State.CLOSED);
        assertThat(mockWebServer.getRequestCount()).isEqualTo(3);

        // 2nd try
        webTestClient
                .get()
                .uri("/api/catalog/id/{id}", productId)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.id")
                .isEqualTo(product.getId())
                .jsonPath("$.productCode")
                .isEqualTo(product.getProductCode())
                .jsonPath("$.productName")
                .isEqualTo(product.getProductName())
                .jsonPath("$.description")
                .isEqualTo(product.getDescription())
                .jsonPath("$.price")
                .isEqualTo(product.getPrice())
                .jsonPath("$.inStock")
                .isEqualTo(true);
        checkHealthStatus("default", CircuitBreaker.State.CLOSED);
        assertThat(mockWebServer.getRequestCount()).isEqualTo(5);
    }

    @Test
    void shouldNotFindProductById() {
        transitionToClosedState("default");
        Long productId = 100L;

        webTestClient
                .get()
                .uri("/api/catalog/id/{id}", productId)
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectHeader()
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.type")
                .isEqualTo("https://api.microservices.com/errors/not-found")
                .jsonPath("$.title")
                .isEqualTo("Product Not Found")
                .jsonPath("$.status")
                .isEqualTo(404)
                .jsonPath("$.detail")
                .isEqualTo("Product with id 100 not found")
                .jsonPath("$.instance")
                .isEqualTo("/api/catalog/id/100")
                .jsonPath("$.timestamp")
                .isNotEmpty()
                .jsonPath("$.errorCategory")
                .isEqualTo("Generic");
        checkHealthStatus("default", CircuitBreaker.State.CLOSED);
    }

    @Test
    void shouldFindProductByProductCode() {
        Product product = savedProductList.getFirst();

        webTestClient
                .get()
                .uri("/api/catalog/productCode/{productCode}", product.getProductCode())
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.id")
                .isEqualTo(product.getId())
                .jsonPath("$.productCode")
                .isEqualTo(product.getProductCode())
                .jsonPath("$.productName")
                .isEqualTo(product.getProductName())
                .jsonPath("$.description")
                .isEqualTo(product.getDescription())
                .jsonPath("$.price")
                .isEqualTo(product.getPrice())
                .jsonPath("$.inStock")
                .isEqualTo(false);
    }

    @Test
    void shouldFindProductByProductCodeWithStock() throws JsonProcessingException {
        Product product = savedProductList.getFirst();

        mockBackendEndpoint(
                200,
                objectMapper.writeValueAsString(
                        new InventoryResponse(product.getProductCode(), 10)));
        webTestClient
                .get()
                .uri(
                        uriBuilder ->
                                uriBuilder
                                        .path("/api/catalog/productCode/{productCode}")
                                        .queryParam("fetchInStock", true)
                                        .build(product.getProductCode()))
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.id")
                .isEqualTo(product.getId())
                .jsonPath("$.productCode")
                .isEqualTo(product.getProductCode())
                .jsonPath("$.productName")
                .isEqualTo(product.getProductName())
                .jsonPath("$.description")
                .isEqualTo(product.getDescription())
                .jsonPath("$.price")
                .isEqualTo(product.getPrice())
                .jsonPath("$.inStock")
                .isEqualTo(true);
    }

    @Test
    void productsShouldExistsByProductCodes() {
        List<String> productCodeList =
                savedProductList.stream().map(Product::getProductCode).toList();

        webTestClient
                .get()
                .uri(
                        uriBuilder -> {
                            uriBuilder.queryParam("productCodes", productCodeList);
                            uriBuilder.path("/api/catalog/exists");
                            return uriBuilder.build();
                        })
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .contentType(MediaType.APPLICATION_JSON)
                .expectBody(Boolean.class)
                .isEqualTo(Boolean.TRUE);
    }

    @Test
    void productsShouldNotExistsByProductCodes() {
        List<String> productCodeList = List.of("P1", "P2", "P3", "P4", "P5");

        webTestClient
                .get()
                .uri(
                        uriBuilder -> {
                            uriBuilder.queryParam("productCodes", productCodeList);
                            uriBuilder.path("/api/catalog/exists");
                            return uriBuilder.build();
                        })
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .contentType(MediaType.APPLICATION_JSON)
                .expectBody(Boolean.class)
                .isEqualTo(Boolean.FALSE);
    }

    @Test
    void shouldCreateNewProduct() {
        ProductRequest productRequest =
                new ProductRequest("code 4", "name 4", "description 4", null, 19.0);
        webTestClient
                .post()
                .uri("/api/catalog")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just(productRequest), ProductRequest.class)
                .exchange()
                .expectStatus()
                .isCreated()
                .expectHeader()
                .exists("Location")
                .expectHeader()
                .contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.id")
                .isNotEmpty()
                .jsonPath("$.productCode")
                .isEqualTo(productRequest.productCode())
                .jsonPath("$.productName")
                .isEqualTo(productRequest.productName())
                .jsonPath("$.description")
                .isEqualTo(productRequest.description())
                .jsonPath("$.price")
                .isEqualTo(productRequest.price());

        await().atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofSeconds(1))
                .pollDelay(Duration.ofSeconds(1))
                .untilAsserted(
                        () ->
                                assertThat(testKafkaListenerConfig.getLatch().getCount())
                                        .isEqualTo(9L));
    }

    @Test
    void shouldThrowConflictForCreateNewProduct() {
        ProductRequest productRequest =
                new ProductRequest("P001", "name 4", "description 4", null, 19.0);

        webTestClient
                .post()
                .uri("/api/catalog")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just(productRequest), ProductRequest.class)
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.CONFLICT)
                .expectHeader()
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.type")
                .isEqualTo("https://api.microservices.com/errors/already-exists")
                .jsonPath("$.title")
                .isEqualTo("Product Already Exists")
                .jsonPath("$.status")
                .isEqualTo(409)
                .jsonPath("$.detail")
                .isEqualTo("Product with id P001 already Exists")
                .jsonPath("$.instance")
                .isEqualTo("/api/catalog")
                .jsonPath("$.timestamp")
                .isNotEmpty()
                .jsonPath("$.errorCategory")
                .isEqualTo("Generic");
    }

    @Test
    void shouldReturn400WhenCreateNewProductWithoutCode() {
        ProductRequest productRequest = new ProductRequest(null, null, null, null, null);

        webTestClient
                .post()
                .uri("/api/catalog")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(productRequest)
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectHeader()
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.type")
                .isEqualTo("about:blank")
                .jsonPath("$.title")
                .isEqualTo("Bad Request")
                .jsonPath("$.status")
                .isEqualTo(400)
                .jsonPath("$.detail")
                .isEqualTo("Invalid request content.")
                .jsonPath("$.instance")
                .isEqualTo("/api/catalog");
    }

    @Test
    void shouldUpdateProduct() {
        Product product = savedProductList.getFirst();

        ProductRequest productRequest =
                new ProductRequest(
                        product.getProductCode(),
                        product.getProductName(),
                        "Updated Catalog",
                        null,
                        100D);

        webTestClient
                .put()
                .uri("/api/catalog/{id}", product.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just(productRequest), ProductRequest.class)
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.id")
                .value(is(product.getId().intValue()))
                .jsonPath("$.productCode")
                .value(is(product.getProductCode()))
                .jsonPath("$.productName")
                .value(is(product.getProductName()))
                .jsonPath("$.description")
                .value(is("Updated Catalog"))
                .jsonPath("$.price")
                .value(is(100.00));
    }

    @Test
    void shouldDeleteProduct() {
        Product product = savedProductList.getFirst();

        webTestClient
                .delete()
                .uri("/api/catalog/{id}", product.getId())
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.id")
                .isEqualTo(product.getId())
                .jsonPath("$.productCode")
                .isEqualTo(product.getProductCode())
                .jsonPath("$.productName")
                .isEqualTo(product.getProductName())
                .jsonPath("$.description")
                .isEqualTo(product.getDescription())
                .jsonPath("$.price")
                .isEqualTo(product.getPrice());
    }

    @Nested
    @DisplayName("product search")
    class ProductSearch {
        @Test
        void shouldSearchProductsByTerm() throws JsonProcessingException {
            mockBackendEndpoint(
                    200,
                    objectMapper.writeValueAsString(List.of(new InventoryResponse("P001", 5))));

            webTestClient
                    .get()
                    .uri("/api/catalog/search?term=name")
                    .exchange()
                    .expectStatus()
                    .isOk()
                    .expectHeader()
                    .contentType(MediaType.APPLICATION_JSON)
                    .expectBody(PagedResult.class)
                    .consumeWith(
                            response -> {
                                PagedResult<?> result = response.getResponseBody();
                                assertThat(result).isNotNull();
                                assertThat(result.data()).isNotNull();
                                assertThat(result.data().size())
                                        .isEqualTo(
                                                3); // All three products have "name" in their name
                                assertThat(result.totalElements()).isEqualTo(3);
                            });
        }

        @Test
        void shouldSearchProductsByPriceRange() throws JsonProcessingException {
            // Setup mock inventory response
            mockBackendEndpoint(
                    200,
                    objectMapper.writeValueAsString(
                            List.of(
                                    new InventoryResponse("P002", 3),
                                    new InventoryResponse("P003", 0))));

            webTestClient
                    .get()
                    .uri("/api/catalog/search?minPrice=10.0&maxPrice=12.0")
                    .exchange()
                    .expectStatus()
                    .isOk()
                    .expectHeader()
                    .contentType(MediaType.APPLICATION_JSON)
                    .expectBody(PagedResult.class)
                    .consumeWith(
                            response -> {
                                PagedResult<?> result = response.getResponseBody();
                                assertThat(result).isNotNull();
                                assertThat(result.data()).isNotNull();
                                assertThat(result.data().size())
                                        .isEqualTo(2); // Products with price between 10.0 and 12.0
                                assertThat(result.totalElements()).isEqualTo(2);
                            });
        }

        @Test
        void shouldSearchByTermAndPriceRange() throws JsonProcessingException {
            // Setup mock inventory response
            mockBackendEndpoint(
                    200,
                    objectMapper.writeValueAsString(
                            List.of(
                                    new InventoryResponse("P001", 5),
                                    new InventoryResponse("P002", 3),
                                    new InventoryResponse("P003", 10))));

            webTestClient
                    .get()
                    .uri("/api/catalog/search?term=name&minPrice=10.0&maxPrice=12.0")
                    .exchange()
                    .expectStatus()
                    .isOk()
                    .expectHeader()
                    .contentType(MediaType.APPLICATION_JSON)
                    .expectBody(PagedResult.class)
                    .consumeWith(
                            response -> {
                                PagedResult<?> result = response.getResponseBody();
                                assertThat(result).isNotNull();
                                assertThat(result.data()).isNotNull();
                                assertThat(result.data().size()).isEqualTo(3);
                                // Products with "name" AND price between 10.0 and 12.0
                                assertThat(result.totalElements()).isEqualTo(3);
                            });
        }

        @Test
        void shouldReturnAllProductsWhenNoSearchCriteriaProvided() throws JsonProcessingException {
            mockBackendEndpoint(
                    200,
                    objectMapper.writeValueAsString(
                            List.of(
                                    new InventoryResponse("P001", 5),
                                    new InventoryResponse("P002", 3),
                                    new InventoryResponse("P003", 0))));

            webTestClient
                    .get()
                    .uri("/api/catalog/search")
                    .exchange()
                    .expectStatus()
                    .isOk()
                    .expectHeader()
                    .contentType(MediaType.APPLICATION_JSON)
                    .expectBody(PagedResult.class)
                    .consumeWith(
                            response -> {
                                PagedResult<?> result = response.getResponseBody();
                                assertThat(result).isNotNull();
                                assertThat(result.data()).isNotNull();
                                assertThat(result.data().size())
                                        .isEqualTo(3); // All products returned
                                assertThat(result.totalElements()).isEqualTo(3);
                            });
        }
    }

    private void mockBackendEndpoint(int responseCode, String body) {
        MockResponse mockResponse =
                new MockResponse()
                        .setResponseCode(responseCode)
                        .setBody(body)
                        .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        mockWebServer.enqueue(mockResponse);
    }
}
