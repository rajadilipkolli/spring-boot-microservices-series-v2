/*** Licensed under Apache-2.0 2021-2023 ***/
package com.example.catalogservice.web.controllers;

import static org.hamcrest.CoreMatchers.is;
import static org.mockito.BDDMockito.given;

import com.example.catalogservice.common.AbstractIntegrationTest;
import com.example.catalogservice.entities.Product;
import com.example.catalogservice.model.response.InventoryDto;
import com.example.catalogservice.repositories.ProductRepository;
import com.example.catalogservice.services.InventoryServiceProxy;
import com.example.common.dtos.ProductDto;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpServerErrorException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class ProductControllerIT extends AbstractIntegrationTest {

    @Autowired private ProductRepository productRepository;

    @MockBean private InventoryServiceProxy inventoryServiceProxy;

    private Flux<Product> productFlux = null;

    @BeforeEach
    void setUp() {

        List<Product> productList =
                List.of(
                        new Product(null, "P001", "name 1", "description 1", 9.0, false),
                        new Product(null, "P002", "name 2", "description 2", 10.0, false),
                        new Product(null, "P003", "name 3", "description 3", 11.0, false));
        productFlux =
                productRepository
                        .deleteAll()
                        .thenMany(productRepository.saveAll(productList))
                        .thenMany(productRepository.findAll());
    }

    @Test
    void shouldFetchAllProducts() {

        given(inventoryServiceProxy.getInventoryByProductCodes(List.of("P001", "P002", "P003")))
                .willReturn(Flux.just(new InventoryDto("P001", 0)));
        List<Product> productList = productFlux.collectList().block();
        webTestClient
                .get()
                .uri("/api/catalog")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBodyList(Product.class)
                .hasSize(productList.size())
                .isEqualTo(productList); // Ensure fetched posts match the expected posts
    }

    @Test
    void shouldFetchAllProductsAsEmpty() {

        productRepository.deleteAll().block();
        webTestClient
                .get()
                .uri("/api/catalog")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBodyList(Product.class)
                .hasSize(0);
    }

    @Test
    void shouldFetchAllProductsWithCircuitBreaker() {

        given(inventoryServiceProxy.getInventoryByProductCodes(List.of("P001", "P002", "P003")))
                .willThrow(new HttpServerErrorException(HttpStatusCode.valueOf(500)));

        webTestClient
                .get()
                .uri("/api/catalog")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBodyList(Product.class)
                .hasSize(0);
    }

    @Test
    void shouldFindProductById() {

        Product product = productFlux.next().block();
        Long productId = product.getId();

        given(inventoryServiceProxy.getInventoryByProductCode(product.getCode()))
                .willReturn(Mono.just(new InventoryDto(product.getCode(), 100)));

        webTestClient
                .get()
                .uri("/api/catalog/id/{id}", productId)
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
    void shouldNotFindProductById() {
        Long productId = 100L;

        webTestClient
                .get()
                .uri("/api/catalog/id/{id}", productId)
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectHeader()
                .contentType("application/problem+json")
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
    }

    @Test
    void shouldFindProductByProductCode() {
        Product product = productFlux.next().block();

        webTestClient
                .get()
                .uri("/api/catalog/productCode/{productCode}", product.getCode())
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .contentType(MediaType.APPLICATION_JSON)
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
    void shouldCreateNewProduct() {
        ProductDto productDto = new ProductDto("code 4", "name 4", "description 4", 19.0);
        webTestClient
                .post()
                .uri("/api/catalog")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just(productDto), ProductDto.class)
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
    void shouldReturn400WhenCreateNewProductWithoutCode() {
        ProductDto productDto = new ProductDto(null, null, null, null);

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
        Product product = productFlux.next().block();
        product.setDescription("Updated Catalog");

        webTestClient
                .put()
                .uri("/api/catalog/{id}", product.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just(product), Product.class)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.id")
                .value(is(product.getId().intValue()))
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
    void shouldDeleteProduct() {
        Product product = productFlux.next().block();

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
}
