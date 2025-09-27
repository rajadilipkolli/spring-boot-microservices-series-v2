/***
<p>
    Licensed under MIT License Copyright (c) 2021-2024 Raja Kolli.
</p>
***/

package com.example.catalogservice.web.controllers;

import static com.example.catalogservice.utils.AppConstants.PROFILE_TEST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.example.catalogservice.entities.Product;
import com.example.catalogservice.exception.ProductNotFoundException;
import com.example.catalogservice.model.payload.ProductDto;
import com.example.catalogservice.model.request.ProductRequest;
import com.example.catalogservice.model.response.PagedResult;
import com.example.catalogservice.model.response.ProductResponse;
import com.example.catalogservice.services.ProductService;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@WebFluxTest(controllers = ProductController.class)
@ActiveProfiles({PROFILE_TEST})
class ProductControllerTest {

    @Autowired private WebTestClient webTestClient;

    @MockitoBean private ProductService productService;

    private List<ProductResponse> productResponseList;

    @BeforeEach
    void setUp() {
        this.productResponseList = new ArrayList<>();
        this.productResponseList.add(
                new ProductResponse(1L, "code 1", "name 1", "description 1", null, 9.0, true));
        this.productResponseList.add(
                new ProductResponse(2L, "code 2", "name 2", "description 2", null, 10.0, true));
        this.productResponseList.add(
                new ProductResponse(3L, "code 3", "name 3", "description 3", null, 11.0, true));
    }

    @Test
    void shouldFetchAllProducts() {
        Page<ProductResponse> page = new PageImpl<>(productResponseList);
        PagedResult<ProductResponse> pagedResult = new PagedResult<>(page);
        given(productService.findAllProducts(0, 10, "id", "asc"))
                .willReturn(Mono.just(pagedResult));

        webTestClient
                .get()
                .uri("/api/catalog")
                .exchange()
                .expectStatus()
                .isOk()
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
                                                            .hasSameSizeAs(productResponseList);
                                                }));
    }

    @Test
    void shouldFindProductById() {
        Long productId = 1L;
        ProductResponse productResponse =
                new ProductResponse(
                        productId, "code 1", "name 1", "description 1", null, 9.0, true);
        given(productService.findProductById(productId)).willReturn(Mono.just(productResponse));

        webTestClient
                .get()
                .uri("/api/catalog/id/{id}", productId)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.id")
                .isEqualTo(productResponse.id())
                .jsonPath("$.productCode")
                .isEqualTo(productResponse.productCode())
                .jsonPath("$.productName")
                .isEqualTo(productResponse.productName())
                .jsonPath("$.description")
                .isEqualTo(productResponse.description())
                .jsonPath("$.price")
                .isEqualTo(productResponse.price());
    }

    @Test
    void shouldReturn404WhenFetchingNonExistingProduct() {
        Long productId = 1L;
        given(productService.findProductById(productId))
                .willThrow(new ProductNotFoundException(productId));

        webTestClient
                .get()
                .uri("/api/catalog/id/{id}", productId)
                .exchange()
                .expectStatus()
                .isNotFound();
    }

    @Test
    void shouldCreateProduct() {
        ProductResponse productResponse =
                new ProductResponse(1L, "code 1", "name 1", "description 1", null, 9.0, true);
        given(productService.saveProduct(any(ProductRequest.class)))
                .willReturn(Mono.just(productResponse));

        ProductRequest productRequest =
                new ProductRequest("code 1", "name 1", "description 1", null, 9.0);
        webTestClient
                .post()
                .uri("/api/catalog")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just(productRequest), ProductRequest.class)
                .exchange()
                .expectStatus()
                .isCreated()
                .expectHeader()
                .contentType(MediaType.APPLICATION_JSON)
                .expectHeader()
                .exists("Location")
                .expectBody()
                .jsonPath("$.id")
                .isNotEmpty()
                .jsonPath("$.productCode")
                .isEqualTo(productResponse.productCode())
                .jsonPath("$.productName")
                .isEqualTo(productResponse.productName())
                .jsonPath("$.description")
                .isEqualTo(productResponse.description())
                .jsonPath("$.price")
                .isEqualTo(productResponse.price());
    }

    @Test
    void shouldReturn400WhenCreateNewProductWithoutCode() throws Exception {
        ProductDto productDto = new ProductDto(null, null, null, 9.0);

        webTestClient
                .post()
                .uri("/api/catalog")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(productDto)
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectHeader()
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.type")
                .isEqualTo("about:blank")
                .jsonPath("$.title")
                .isEqualTo("Validation Error")
                .jsonPath("$.status")
                .isEqualTo(400)
                .jsonPath("$.detail")
                .isEqualTo("Invalid request content.")
                .jsonPath("$.instance")
                .isEqualTo("/api/catalog");
    }

    @Test
    void shouldHandleIdempotentProductCreation() {
        ProductResponse existingProductResponse =
                new ProductResponse(
                        1L,
                        "code-123",
                        "Existing Product",
                        "This product already exists",
                        null,
                        19.99,
                        true);
        ProductRequest productRequest =
                new ProductRequest(
                        "code-123", "Existing Product", "This product already exists", null, 19.99);

        // Mock the service to return the existing product when trying to save with the same product
        // code
        given(productService.saveProduct(any(ProductRequest.class)))
                .willReturn(Mono.just(existingProductResponse));

        // First request - should create normally
        webTestClient
                .post()
                .uri("/api/catalog")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just(productRequest), ProductRequest.class)
                .exchange()
                .expectStatus()
                .isCreated()
                .expectHeader()
                .contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.id")
                .isEqualTo(existingProductResponse.id())
                .jsonPath("$.productCode")
                .isEqualTo(existingProductResponse.productCode());

        // Second request with same product code - should be idempotent and return the same product
        webTestClient
                .post()
                .uri("/api/catalog")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just(productRequest), ProductRequest.class)
                .exchange()
                .expectStatus()
                .isCreated() // We're returning 201 Created in both cases for consistency
                .expectHeader()
                .contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.id")
                .isEqualTo(existingProductResponse.id())
                .jsonPath("$.productCode")
                .isEqualTo(existingProductResponse.productCode());
    }

    @Test
    void shouldUpdateProduct() {
        Long productId = 1L;
        Product product =
                new Product()
                        .setId(productId)
                        .setProductCode("code 1")
                        .setProductName("Updated name")
                        .setDescription("description 1")
                        .setPrice(9.0);
        ProductRequest productRequest =
                new ProductRequest("code 1", "Updated name", "description 1", null, 9.0);
        ProductResponse productResponse =
                new ProductResponse(
                        productId, "code 1", "Updated name", "description 1", null, 9.0, true);
        given(productService.findById(productId)).willReturn(Mono.just(product));
        given(productService.updateProduct(any(ProductRequest.class), any(Product.class)))
                .willReturn(Mono.just(productResponse));

        webTestClient
                .put()
                .uri("/api/catalog/{id}", productId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just(productRequest), ProductRequest.class)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.id")
                .value(is(1))
                .jsonPath("$.productCode")
                .value(is(product.getProductCode()))
                .jsonPath("$.productName")
                .value(is(product.getProductName()))
                .jsonPath("$.description")
                .value(is(product.getDescription()))
                .jsonPath("$.price")
                .value(is(product.getPrice()));
    }

    @Test
    void shouldReturn404WhenUpdatingNonExistingProduct() {
        Long productId = 1L;
        given(productService.findById(productId)).willReturn(Mono.empty());
        ProductRequest productRequest =
                new ProductRequest("code 1", "Updated name", "description 1", null, 9.0);

        webTestClient
                .put()
                .uri("/api/catalog/{id}", productId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just(productRequest), ProductRequest.class)
                .exchange()
                .expectStatus()
                .isNotFound();
    }

    @Test
    void shouldDeleteProduct() {
        Long productId = 1L;
        Product product =
                new Product()
                        .setId(productId)
                        .setProductCode("code 1")
                        .setProductName("Updated name")
                        .setDescription("description 1")
                        .setPrice(9.0);
        ProductResponse productResponse =
                new ProductResponse(1L, "code 1", "Updated name", "description 1", null, 9.0, true);
        given(productService.findByIdWithMapping(productId)).willReturn(Mono.just(productResponse));
        given(productService.deleteProductById(product.getId())).willReturn(Mono.empty());

        webTestClient
                .delete()
                .uri("/api/catalog/{id}", product.getId())
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
                .isEqualTo(product.getPrice());
    }

    @Test
    void shouldReturn404WhenDeletingNonExistingProduct() {
        Long productId = 1L;
        given(productService.findByIdWithMapping(productId)).willReturn(Mono.empty());

        webTestClient
                .delete()
                .uri("/api/catalog/{id}", productId)
                .exchange()
                .expectStatus()
                .isNotFound();
    }

// ----- Additional tests for diffed endpoints in ProductController -----
// Note: Testing stack -> JUnit 5 (Jupiter), Spring Boot WebFlux (@WebFluxTest) with WebTestClient,
// Mockito via @MockitoBean, and assertions via AssertJ/Hamcrest.

    @Test
    void shouldGetProductByProductCode_withoutFetchInStock() {
        ProductResponse productResponse =
                new ProductResponse(10L, "PROD001", "name 1", "desc", null, 9.99, true);
        given(productService.findProductByProductCode("PROD001", false))
                .willReturn(Mono.just(productResponse));

        webTestClient
                .get()
                .uri("/api/catalog/productCode/{code}", "PROD001")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.productCode")
                .isEqualTo("PROD001");

        verify(productService).findProductByProductCode("PROD001", false);
    }

    @Test
    void shouldGetProductByProductCode_withFetchInStockTrue() {
        ProductResponse productResponse =
                new ProductResponse(11L, "PROD002", "name 2", "desc", null, 19.99, true);
        given(productService.findProductByProductCode("PROD002", true))
                .willReturn(Mono.just(productResponse));

        webTestClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/catalog/productCode/{code}")
                        .queryParam("fetchInStock", true)
                        .build("PROD002"))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.productCode")
                .isEqualTo("PROD002");

        verify(productService).findProductByProductCode("PROD002", true);
    }

    @Test
    void shouldGetProductByProductCode_withDelay() {
        ProductResponse productResponse =
                new ProductResponse(12L, "PROD003", "name 3", "desc", null, 29.99, true);
        given(productService.findProductByProductCode("PROD003", false))
                .willReturn(Mono.just(productResponse));

        webTestClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/catalog/productCode/{code}")
                        .queryParam("delay", 1)
                        .build("PROD003"))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.productCode")
                .isEqualTo("PROD003");

        verify(productService).findProductByProductCode("PROD003", false);
    }

    @Test
    void shouldGetProductByProductCode_ignoreNegativeDelay() {
        ProductResponse productResponse =
                new ProductResponse(13L, "PROD004", "name 4", "desc", null, 39.99, true);
        given(productService.findProductByProductCode("PROD004", false))
                .willReturn(Mono.just(productResponse));

        webTestClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/catalog/productCode/{code}")
                        .queryParam("delay", -5)
                        .build("PROD004"))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.productCode")
                .isEqualTo("PROD004");

        verify(productService).findProductByProductCode("PROD004", false);
    }

    @Test
    void shouldReturnTrueWhenProductsExistByCodes() {
        var codes = Arrays.asList("PROD001", "PROD002");
        given(productService.productExistsByProductCodes(codes)).willReturn(Mono.just(true));

        webTestClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/catalog/exists")
                        .queryParam("productCodes", "PROD001", "PROD002")
                        .build())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(Boolean.class)
                .isEqualTo(true);

        verify(productService).productExistsByProductCodes(codes);
    }

    @Test
    void shouldReturnFalseWhenProductsDoNotExistByCodes() {
        var codes = Arrays.asList("DOES_NOT_EXIST_1", "DOES_NOT_EXIST_2");
        given(productService.productExistsByProductCodes(codes)).willReturn(Mono.just(false));

        webTestClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/catalog/exists")
                        .queryParam("productCodes", "DOES_NOT_EXIST_1", "DOES_NOT_EXIST_2")
                        .build())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(Boolean.class)
                .isEqualTo(false);
    }

    @Test
    void shouldHandleEmptyProductCodesListOnExists() {
        // Note: Depending on binding, this may resolve to an empty list.
        given(productService.productExistsByProductCodes(Collections.emptyList()))
                .willReturn(Mono.just(false));

        webTestClient
                .get()
                .uri("/api/catalog/exists?productCodes=")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(Boolean.class)
                .isEqualTo(false);
    }

    @Test
    void shouldGenerateRandomProducts_whenServiceReturnsTrue() {
        given(productService.generateProducts()).willReturn(Mono.just(true));

        webTestClient
                .get()
                .uri("/api/catalog/generate")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(Boolean.class)
                .isEqualTo(true);

        verify(productService).generateProducts();
    }

    @Test
    void shouldGenerateRandomProducts_whenServiceReturnsFalse() {
        given(productService.generateProducts()).willReturn(Mono.just(false));

        webTestClient
                .get()
                .uri("/api/catalog/generate")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(Boolean.class)
                .isEqualTo(false);
    }

    @Test
    void shouldSearchByTermAndPriceRange() {
        var page = new PageImpl<>(productResponseList);
        var pagedResult = new PagedResult<ProductResponse>(page);

        given(productService.searchProductsByTermAndPriceRange("laptop", 100.0, 500.0, 0, 10, "id", "asc"))
                .willReturn(Mono.just(pagedResult));

        webTestClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/catalog/search")
                        .queryParam("term", "laptop")
                        .queryParam("minPrice", 100.0)
                        .queryParam("maxPrice", 500.0)
                        .build())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(PagedResult.class);

        verify(productService)
                .searchProductsByTermAndPriceRange("laptop", 100.0, 500.0, 0, 10, "id", "asc");
    }

    @Test
    void shouldSearchByTermOnly() {
        var page = new PageImpl<>(productResponseList);
        var pagedResult = new PagedResult<ProductResponse>(page);

        given(productService.searchProductsByTerm("laptop", 0, 10, "id", "asc"))
                .willReturn(Mono.just(pagedResult));

        webTestClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/catalog/search")
                        .queryParam("term", "laptop")
                        .build())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(PagedResult.class);

        verify(productService).searchProductsByTerm("laptop", 0, 10, "id", "asc");
    }

    @Test
    void shouldSearchByPriceRangeOnly() {
        var page = new PageImpl<>(productResponseList);
        var pagedResult = new PagedResult<ProductResponse>(page);

        given(productService.searchProductsByPriceRange(100.0, 500.0, 0, 10, "id", "asc"))
                .willReturn(Mono.just(pagedResult));

        webTestClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/catalog/search")
                        .queryParam("minPrice", 100.0)
                        .queryParam("maxPrice", 500.0)
                        .build())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(PagedResult.class);

        verify(productService).searchProductsByPriceRange(100.0, 500.0, 0, 10, "id", "asc");
    }

    @Test
    void shouldReturnAllProductsWhenNoCriteria() {
        var page = new PageImpl<>(productResponseList);
        var pagedResult = new PagedResult<ProductResponse>(page);

        given(productService.findAllProducts(0, 10, "id", "asc"))
                .willReturn(Mono.just(pagedResult));

        webTestClient
                .get()
                .uri("/api/catalog/search")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(PagedResult.class);

        verify(productService).findAllProducts(0, 10, "id", "asc");
    }

    @Test
    void shouldIgnoreEmptyTermAndSearchByPriceRange() {
        var page = new PageImpl<>(productResponseList);
        var pagedResult = new PagedResult<ProductResponse>(page);

        given(productService.searchProductsByPriceRange(100.0, 500.0, 0, 10, "id", "asc"))
                .willReturn(Mono.just(pagedResult));

        webTestClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/catalog/search")
                        .queryParam("term", "")
                        .queryParam("minPrice", 100.0)
                        .queryParam("maxPrice", 500.0)
                        .build())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(PagedResult.class);

        verify(productService).searchProductsByPriceRange(100.0, 500.0, 0, 10, "id", "asc");
    }

    @Test
    void shouldSearchByTermWithCustomPaginationAndSorting() {
        var page = new PageImpl<>(productResponseList);
        var pagedResult = new PagedResult<ProductResponse>(page);

        given(productService.searchProductsByTerm("laptop", 1, 5, "name", "desc"))
                .willReturn(Mono.just(pagedResult));

        webTestClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/catalog/search")
                        .queryParam("term", "laptop")
                        .queryParam("pageNo", 1)
                        .queryParam("pageSize", 5)
                        .queryParam("sortBy", "name")
                        .queryParam("sortDir", "desc")
                        .build())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(PagedResult.class);

        verify(productService).searchProductsByTerm("laptop", 1, 5, "name", "desc");
    }

    @Test
    void shouldHandlePartialPriceRangeOnlyMinPriceWithTerm() {
        var page = new PageImpl<>(productResponseList);
        var pagedResult = new PagedResult<ProductResponse>(page);

        given(productService.searchProductsByTerm("laptop", 0, 10, "id", "asc"))
                .willReturn(Mono.just(pagedResult));

        webTestClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/catalog/search")
                        .queryParam("term", "laptop")
                        .queryParam("minPrice", 100.0)
                        .build())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(PagedResult.class);

        verify(productService).searchProductsByTerm("laptop", 0, 10, "id", "asc");
    }

    @Test
    void shouldHandlePartialPriceRangeOnlyMaxPrice() {
        var page = new PageImpl<>(productResponseList);
        var pagedResult = new PagedResult<ProductResponse>(page);

        given(productService.findAllProducts(0, 10, "id", "asc"))
                .willReturn(Mono.just(pagedResult));

        webTestClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/catalog/search")
                        .queryParam("maxPrice", 500.0)
                        .build())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(PagedResult.class);

        verify(productService).findAllProducts(0, 10, "id", "asc");
    }

}
