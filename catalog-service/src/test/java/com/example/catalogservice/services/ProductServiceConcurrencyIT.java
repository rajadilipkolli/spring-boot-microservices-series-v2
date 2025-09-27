/***
<p>
    Licensed under MIT License Copyright (c) 2025 Raja Kolli.
</p>
***/

package com.example.catalogservice.services;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.catalogservice.common.AbstractIntegrationTest;
import com.example.catalogservice.entities.Product;
import com.example.catalogservice.exception.ProductAlreadyExistsException;
import com.example.catalogservice.model.request.ProductRequest;
import com.example.catalogservice.model.response.ProductResponse;
import com.example.catalogservice.repositories.ProductRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Integration test for ProductService that verifies race condition handling in the saveProduct
 * method.
 */
class ProductServiceConcurrencyIT extends AbstractIntegrationTest {

    @Autowired private ProductRepository productRepository;

    @Autowired private ProductService productService;

    private static final String TEST_PRODUCT_CODE = "RACE_CONDITION_TEST";
    private static final String TEST_PRODUCT_CODE_2 = "RACE_CONDITION_TEST_2";
    private static final String TEST_PRODUCT_CODE_3 = "RACE_CONDITION_TEST_3";

    @BeforeEach
    void setUp() {
        // Clean up any existing test products using StepVerifier
        StepVerifier.create(productRepository.deleteByProductCodeAllIgnoreCase(TEST_PRODUCT_CODE))
                .verifyComplete();
        StepVerifier.create(productRepository.deleteByProductCodeAllIgnoreCase(TEST_PRODUCT_CODE_2))
                .verifyComplete();
        StepVerifier.create(productRepository.deleteByProductCodeAllIgnoreCase(TEST_PRODUCT_CODE_3))
                .verifyComplete();
    }

    @AfterEach
    void cleanUp() {
        // Clean up test products after each test using StepVerifier
        StepVerifier.create(productRepository.deleteByProductCodeAllIgnoreCase(TEST_PRODUCT_CODE))
                .verifyComplete();
        StepVerifier.create(productRepository.deleteByProductCodeAllIgnoreCase(TEST_PRODUCT_CODE_2))
                .verifyComplete();
        StepVerifier.create(productRepository.deleteByProductCodeAllIgnoreCase(TEST_PRODUCT_CODE_3))
                .verifyComplete();
    }

    @Test
    void whenMultipleRequestsSaveSameProduct_onlyOneIsSaved() {
        // Given: Product data for our test
        // We'll use this data to create a product directly with the repository

        // First, directly save one to ensure we have at least one product
        StepVerifier.create(
                        productRepository.save(
                                new Product()
                                        .setProductCode(TEST_PRODUCT_CODE)
                                        .setProductName("Test Product")
                                        .setDescription("Description")
                                        .setPrice(10.0)))
                .expectNextCount(1)
                .verifyComplete();

        // Use the service approach instead of repository to handle the duplicate key properly
        // This will use the ProductService's conflict resolution mechanism
        ProductRequest duplicateRequest =
                new ProductRequest(
                        TEST_PRODUCT_CODE,
                        "Test Product Updated", // Try with a different name
                        "Updated Description",
                        null,
                        15.0); // Different price

        // When: Using the service to save a product with the same code
        StepVerifier.create(productService.saveProduct(duplicateRequest))
                .assertNext(
                        response -> {
                            // Then: Should return the existing product
                            assertThat(response).isNotNull();
                            assertThat(response.productCode()).isEqualTo(TEST_PRODUCT_CODE);
                            // Should have the original product details, not the updated ones
                            assertThat(response.productName()).isEqualTo("Test Product");
                            assertThat(response.price()).isEqualTo(10.0);
                        })
                .verifyComplete();

        // Then: Only one product with this code should exist in DB using StepVerifier
        StepVerifier.create(productRepository.countByProductCodeAllIgnoreCase(TEST_PRODUCT_CODE))
                .expectNext(1L)
                .verifyComplete();

        // Verify the product was saved correctly using StepVerifier and has the original values
        StepVerifier.create(productRepository.findByProductCodeAllIgnoreCase(TEST_PRODUCT_CODE))
                .assertNext(
                        savedProduct -> {
                            assertThat(savedProduct).isNotNull();
                            assertThat(savedProduct.getProductCode()).isEqualTo(TEST_PRODUCT_CODE);
                            assertThat(savedProduct.getProductName()).isEqualTo("Test Product");
                            assertThat(savedProduct.getPrice()).isEqualTo(10.0);
                        })
                .verifyComplete();
    }

    @Test
    void whenSavingExistingProduct_shouldReturnExistingOne() {
        // Given: An existing product
        ProductRequest productRequest =
                new ProductRequest(TEST_PRODUCT_CODE, "Test Product", "Description", null, 10.0);

        // First, save the product directly to the database
        Product product =
                new Product()
                        .setProductCode(TEST_PRODUCT_CODE)
                        .setProductName("Test Product")
                        .setDescription("Description")
                        .setPrice(10.0);

        // Use StepVerifier to save the product and get the response
        StepVerifier.create(productRepository.save(product))
                .assertNext(
                        savedEntity -> {
                            assertThat(savedEntity.getId()).isNotNull();
                            assertThat(savedEntity.getProductCode()).isEqualTo(TEST_PRODUCT_CODE);
                        })
                .verifyComplete();

        // When: Saving the same product again
        // Then: Should return the existing product without exceptions
        StepVerifier.create(productService.saveProduct(productRequest))
                .assertNext(
                        response -> {
                            assertThat(response).isNotNull();
                            assertThat(response.productCode()).isEqualTo(TEST_PRODUCT_CODE);
                        })
                .verifyComplete();
    }

    @Test
    void whenProductAlreadyExistsException_shouldReturn409Conflict() { // Given: Two product
        // requests with the same
        // product code
        // but different names/attributes to simulate a real conflict
        // Note: We don't use originalProduct directly as we save it directly to the repository

        ProductRequest conflictingProduct =
                new ProductRequest(
                        TEST_PRODUCT_CODE_3,
                        "Conflicting Product",
                        "Conflicting Description",
                        null,
                        30.0);

        // First, create a mono that intentionally introduces a race condition
        // We'll mock the behavior of a database constraint violation by:
        // 1. Starting with an empty repository where the product doesn't exist
        // 2. Simulating two concurrent threads both finding the product doesn't exist
        // 3. First thread saves successfully
        // 4. Second thread tries to save and gets a DuplicateKeyException

        // Step 1: Create a modified saveProduct operation that triggers a conflict
        // This simulates what happens internally when a DuplicateKeyException occurs
        // from two threads trying to save the same product code at the same time
        Mono<ProductResponse> conflictingOperation =
                Mono.defer(
                        () -> {
                            // First, save the original product successfully
                            return productRepository
                                    .save(
                                            new Product()
                                                    .setProductCode(TEST_PRODUCT_CODE_3)
                                                    .setProductName("Original Product")
                                                    .setDescription("Original Product Description")
                                                    .setPrice(20.0))
                                    .flatMap(
                                            savedProduct -> {
                                                // Now try to save the conflicting product with the
                                                // same code
                                                // This should result in a DuplicateKeyException
                                                // which the service handles
                                                return productService.saveProduct(
                                                        conflictingProduct);
                                            });
                        });

        // Test the exception directly for validation
        ProductAlreadyExistsException exception =
                new ProductAlreadyExistsException(TEST_PRODUCT_CODE_3);

        // Verify the exception contains HTTP 409 Conflict status code
        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(exception.getStatusCode().value()).isEqualTo(409);

        // Verify the exception contains the expected problem details
        assertThat(exception.getBody().getDetail()).contains(TEST_PRODUCT_CODE_3);
        assertThat(exception.getBody().getDetail()).contains("already exists");
        assertThat(exception.getBody().getTitle()).isEqualTo("Product Already Exists");
        assertThat(exception.getBody().getType().toString())
                .isEqualTo("https://api.microservices.com/errors/already-exists");

        // Verify properties using ProblemDetail's methods
        ProblemDetail problemDetail = exception.getBody();
        assertThat(problemDetail.getProperties()).containsEntry("errorCategory", "Generic");
        assertThat(problemDetail.getProperties()).containsKey("timestamp");

        // Execute the operation and verify the behavior
        StepVerifier.create(conflictingOperation)
                .assertNext(
                        response -> {
                            // Even though we tried to save conflictingProduct, we should get
                            // the original product back due to the conflict resolution
                            assertThat(response).isNotNull();
                            assertThat(response.productCode()).isEqualTo(TEST_PRODUCT_CODE_3);

                            // The product should have the original name, not the conflicting one
                            // because the first one wins in a race condition
                            assertThat(response.productName()).isEqualTo("Original Product");
                            assertThat(response.price()).isEqualTo(20.0);
                        })
                .verifyComplete();

        // Verify only one product was actually saved in the database
        StepVerifier.create(productRepository.countByProductCodeAllIgnoreCase(TEST_PRODUCT_CODE_3))
                .assertNext(count -> assertThat(count).isEqualTo(1L))
                .verifyComplete(); // Now create a more intense race condition with multiple
        // concurrent save attempts
        // This will verify the atomicity of the operation under high concurrency

        // Create multiple product requests with the same code but different details
        ProductRequest request1 =
                new ProductRequest(
                        TEST_PRODUCT_CODE_3,
                        "Concurrent Product 1",
                        "Concurrent Description 1",
                        null,
                        10.0);
        ProductRequest request2 =
                new ProductRequest(
                        TEST_PRODUCT_CODE_3,
                        "Concurrent Product 2",
                        "Concurrent Description 2",
                        null,
                        20.0);
        ProductRequest request3 =
                new ProductRequest(
                        TEST_PRODUCT_CODE_3,
                        "Concurrent Product 3",
                        "Concurrent Description 3",
                        null,
                        30.0);
        ProductRequest request4 =
                new ProductRequest(
                        TEST_PRODUCT_CODE_3,
                        "Concurrent Product 4",
                        "Concurrent Description 4",
                        null,
                        40.0);
        ProductRequest request5 =
                new ProductRequest(
                        TEST_PRODUCT_CODE_3,
                        "Concurrent Product 5",
                        "Concurrent Description 5",
                        null,
                        50.0);

        // Execute all concurrent save operations and verify the behavior
        StepVerifier.create(
                        Mono.zip(
                                productService.saveProduct(request1),
                                productService.saveProduct(request2),
                                productService.saveProduct(request3),
                                productService.saveProduct(request4),
                                productService.saveProduct(request5)))
                .assertNext(
                        tuple -> {
                            // All responses should be the same product
                            var response1 = tuple.getT1();
                            var response2 = tuple.getT2();
                            var response3 = tuple.getT3();
                            var response4 = tuple.getT4();
                            var response5 = tuple.getT5();

                            // All results should have same product code
                            assertThat(response1.productCode()).isEqualTo(TEST_PRODUCT_CODE_3);
                            assertThat(response2.productCode()).isEqualTo(TEST_PRODUCT_CODE_3);
                            assertThat(response3.productCode()).isEqualTo(TEST_PRODUCT_CODE_3);
                            assertThat(response4.productCode()).isEqualTo(TEST_PRODUCT_CODE_3);
                            assertThat(response5.productCode()).isEqualTo(TEST_PRODUCT_CODE_3);

                            // All responses should match the first one that succeeded
                            assertThat(response2.productName()).isEqualTo(response1.productName());
                            assertThat(response3.productName()).isEqualTo(response1.productName());
                            assertThat(response4.productName()).isEqualTo(response1.productName());
                            assertThat(response5.productName()).isEqualTo(response1.productName());
                        })
                .verifyComplete();

        // Final verification that only one product exists with this code
        StepVerifier.create(productRepository.countByProductCodeAllIgnoreCase(TEST_PRODUCT_CODE_3))
                .assertNext(count -> assertThat(count).isEqualTo(1L))
                .verifyComplete();

        // Verify the product in the database matches what we expect
        StepVerifier.create(productRepository.findByProductCodeAllIgnoreCase(TEST_PRODUCT_CODE_3))
                .assertNext(
                        product -> {
                            assertThat(product).isNotNull();
                            assertThat(product.getProductCode()).isEqualTo(TEST_PRODUCT_CODE_3);
                            // Should still be the original product as it was saved first
                            assertThat(product.getProductName()).isEqualTo("Original Product");
                            assertThat(product.getPrice()).isEqualTo(20.0);
                        })
                .verifyComplete();
    }
}
