/***
<p>
    Licensed under MIT License Copyright (c) 2021-2023 Raja Kolli.
</p>
***/

package com.example.catalogservice.web.controllers;

import static com.example.catalogservice.utils.AppConstants.PROFILE_TEST;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.example.catalogservice.entities.Product;
import com.example.catalogservice.exception.ProductNotFoundException;
import com.example.catalogservice.model.request.ProductRequest;
import com.example.catalogservice.model.response.PagedResult;
import com.example.catalogservice.model.response.ProductResponse;
import com.example.catalogservice.services.ProductService;
import com.example.common.dtos.ProductDto;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@WebFluxTest(controllers = ProductController.class)
@ActiveProfiles({PROFILE_TEST})
class ProductControllerTest {

    @Autowired private WebTestClient webTestClient;

    @MockBean private ProductService productService;

    private List<ProductResponse> productResponseList;

    @BeforeEach
    void setUp() {
        this.productResponseList = new ArrayList<>();
        this.productResponseList.add(
                new ProductResponse(1L, "code 1", "name 1", "description 1", 9.0, true));
        this.productResponseList.add(
                new ProductResponse(2L, "code 2", "name 2", "description 2", 10.0, true));
        this.productResponseList.add(
                new ProductResponse(3L, "code 3", "name 3", "description 3", 11.0, true));
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
                        response -> {
                            assertAll(
                                    () -> assertTrue(response.isFirst()),
                                    () -> assertTrue(response.isLast()),
                                    () -> assertFalse(response.hasNext()),
                                    () -> assertFalse(response.hasPrevious()),
                                    () -> assertEquals(3, response.totalElements()),
                                    () -> assertEquals(1, response.pageNumber()),
                                    () -> assertEquals(1, response.totalPages()),
                                    () ->
                                            assertEquals(
                                                    productResponseList.size(),
                                                    response.data().size()));
                        });
    }

    @Test
    void shouldFindProductById() {
        Long productId = 1L;
        ProductResponse productResponse =
                new ProductResponse(productId, "code 1", "name 1", "description 1", 9.0, true);
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
                .jsonPath("$.code")
                .isEqualTo(productResponse.code())
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
                new ProductResponse(1L, "code 1", "name 1", "description 1", 9.0, true);
        given(productService.saveProduct(any(ProductRequest.class)))
                .willReturn(Mono.just(productResponse));

        ProductDto productDto = new ProductDto("code 1", "name 1", "description 1", 9.0);
        webTestClient
                .post()
                .uri("/api/catalog")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just(productDto), ProductDto.class)
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
                .jsonPath("$.code")
                .isEqualTo(productDto.code())
                .jsonPath("$.productName")
                .isEqualTo(productDto.productName())
                .jsonPath("$.description")
                .isEqualTo(productDto.description())
                .jsonPath("$.price")
                .isEqualTo(productDto.price());
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
        Long productId = 1L;
        Product product = new Product(1L, "code 1", "Updated name", "description 1", 9.0, true);
        ProductDto productDto = new ProductDto("code 1", "Updated name", "description 1", 9.0);
        ProductResponse productResponse =
                new ProductResponse(1L, "code 1", "Updated name", "description 1", 9.0, true);
        given(productService.findById(productId)).willReturn(Mono.just(productResponse));
        given(productService.updateProduct(any(ProductRequest.class), any(ProductResponse.class)))
                .willReturn(Mono.just(productResponse));

        webTestClient
                .put()
                .uri("/api/catalog/{id}", product.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just(productDto), ProductDto.class)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.id")
                .value(is(1))
                .jsonPath("$.code")
                .value(is(product.getCode()))
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
        Product product =
                new Product(productId, "code 1", "Updated name", "description 1", 9.0, true);

        webTestClient
                .put()
                .uri("/api/catalog/{id}", product.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just(product), Product.class)
                .exchange()
                .expectStatus()
                .isNotFound();
    }

    @Test
    void shouldDeleteProduct() {
        Long productId = 1L;
        Product product = new Product(1L, "code 1", "Updated name", "description 1", 9.0, true);
        ProductResponse productResponse =
                new ProductResponse(1L, "code 1", "Updated name", "description 1", 9.0, true);
        given(productService.findById(productId)).willReturn(Mono.just(productResponse));
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
                .jsonPath("$.code")
                .isEqualTo(product.getCode())
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
        given(productService.findById(productId)).willReturn(Mono.empty());

        webTestClient
                .delete()
                .uri("/api/catalog/{id}", productId)
                .exchange()
                .expectStatus()
                .isNotFound();
    }
}
