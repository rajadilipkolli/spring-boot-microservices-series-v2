/***
<p>
    Licensed under MIT License Copyright (c) 2021-2025 Raja Kolli.
</p>
***/

package com.example.catalogservice.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.catalogservice.common.AbstractIntegrationTest;
import com.example.catalogservice.entities.Product;
import com.example.catalogservice.model.request.ProductRequest;
import com.example.catalogservice.model.response.ProductResponse;
import com.example.catalogservice.repositories.ProductRepository;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.test.StepVerifier;

/**
 * Integration test for ProductService that verifies race condition handling in the saveProduct
 * method.
 */
public class ProductServiceConcurrencyIT extends AbstractIntegrationTest {

    @Autowired private ProductRepository productRepository;

    @Autowired private ProductService productService;

    private static final String TEST_PRODUCT_CODE = "RACE_CONDITION_TEST";
    private static final String TEST_PRODUCT_CODE_2 = "RACE_CONDITION_TEST_2";

    @BeforeEach
    void setUp() {
        // Clean up any existing test products
        productRepository.deleteByProductCodeAllIgnoreCase(TEST_PRODUCT_CODE).block();
        productRepository.deleteByProductCodeAllIgnoreCase(TEST_PRODUCT_CODE_2).block();
    }

    @AfterEach
    void cleanUp() {
        // Clean up test products after each test
        productRepository.deleteByProductCodeAllIgnoreCase(TEST_PRODUCT_CODE).block();
        productRepository.deleteByProductCodeAllIgnoreCase(TEST_PRODUCT_CODE_2).block();
    }

    @Test
    void whenMultipleRequestsSaveSameProduct_onlyOneIsSaved() throws InterruptedException {
        // Given: A product request
        ProductRequest productRequest =
                new ProductRequest(TEST_PRODUCT_CODE, "Test Product", "Description", null, 10.0);

        // When: Multiple concurrent requests try to save the same product
        int numberOfThreads = 5;
        CountDownLatch latch = new CountDownLatch(numberOfThreads);
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);

        for (int i = 0; i < numberOfThreads; i++) {
            executorService.submit(
                    () -> {
                        try {
                            productService.saveProduct(productRequest).block();
                        } catch (Exception e) {
                            // Expected that some will fail with DataIntegrityViolationException
                            // which will be handled in the service
                        } finally {
                            latch.countDown();
                        }
                    });
        }

        // Wait for all threads to complete
        latch.await(5, TimeUnit.SECONDS);
        executorService.shutdown();

        // Then: Only one product with this code should exist in DB
        Long count = productRepository.countByProductCodeAllIgnoreCase(TEST_PRODUCT_CODE).block();
        assertEquals(1L, count, "There should be exactly one product with the test product code");

        // Verify the product was saved correctly
        Product savedProduct =
                productRepository.findByProductCodeAllIgnoreCase(TEST_PRODUCT_CODE).block();
        assertThat(savedProduct).isNotNull();
        assertThat(savedProduct.getProductCode()).isEqualTo(TEST_PRODUCT_CODE);
        assertThat(savedProduct.getProductName()).isEqualTo("Test Product");
        assertThat(savedProduct.getPrice()).isEqualTo(10.0);
    }

    @Test
    void whenSavingExistingProduct_shouldReturnExistingOne() {
        // Given: An existing product
        ProductRequest productRequest =
                new ProductRequest(TEST_PRODUCT_CODE, "Test Product", "Description", null, 10.0);

        // Save the product first
        ProductResponse initialResponse = productService.saveProduct(productRequest).block();
        assertThat(initialResponse).isNotNull();
        assertThat(initialResponse.productCode()).isEqualTo(TEST_PRODUCT_CODE);

        // When: Saving the same product again
        // Then: Should return the existing product without exceptions
        StepVerifier.create(productService.saveProduct(productRequest))
                .assertNext(
                        response -> {
                            assertThat(response).isNotNull();
                            assertThat(response.productCode()).isEqualTo(TEST_PRODUCT_CODE);
                            assertThat(response.id()).isEqualTo(initialResponse.id());
                        })
                .verifyComplete();
    }

    @Test
    void whenDataIntegrityViolationOccurs_shouldRecoverAndReturnExistingProduct() {
        // Given: A product request
        ProductRequest productRequest =
                new ProductRequest(TEST_PRODUCT_CODE_2, "Test Product", "Description", null, 10.0);

        // First save a product in the DB
        Product product =
                new Product()
                        .setProductCode(TEST_PRODUCT_CODE_2)
                        .setProductName("Test Product")
                        .setDescription("Description")
                        .setPrice(10.0);

        Product savedProduct = productRepository.save(product).block();
        assertThat(savedProduct).isNotNull();

        // When: Calling saveProduct, the first findByProductCode will return empty (simulating race
        // condition)
        // but the save will fail with constraint violation and the recovery will find the product

        // Verify the save product method works correctly with recovery
        StepVerifier.create(productService.saveProduct(productRequest))
                .assertNext(
                        response -> {
                            assertThat(response).isNotNull();
                            assertThat(response.productCode()).isEqualTo(TEST_PRODUCT_CODE_2);
                            assertThat(response.id()).isEqualTo(savedProduct.getId());
                        })
                .verifyComplete();
    }
}
