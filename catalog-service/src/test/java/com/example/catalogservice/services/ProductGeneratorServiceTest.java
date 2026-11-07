/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.catalogservice.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

import com.example.catalogservice.model.request.ProductRequest;
import com.example.catalogservice.model.response.ProductResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class ProductGeneratorServiceTest {

    @Mock private ProductService productService;

    private ProductGeneratorService generatorService;

    @Captor private ArgumentCaptor<ProductRequest> productCaptor;

    @BeforeEach
    void setUp() {
        generatorService = new ProductGeneratorService(productService);
    }

    @Test
    void shouldGenerateRandomProductsWithDefaultBatchSize() {
        given(productService.saveProduct(any(ProductRequest.class)))
                .willAnswer(
                        invocation -> {
                            ProductRequest request = invocation.getArgument(0);
                            return Mono.just(
                                    new ProductResponse(
                                            1L,
                                            request.productCode(),
                                            request.productName(),
                                            request.description(),
                                            null,
                                            request.price(),
                                            true));
                        });

        StepVerifier.create(generatorService.generateAndSaveProducts("test-batch-123", null))
                .expectSubscription()
                .expectNext(Boolean.TRUE)
                .verifyComplete();

        then(productService).should(times(100)).saveProduct(productCaptor.capture());

        List<ProductRequest> capturedProducts = productCaptor.getAllValues();
        assertThat(capturedProducts)
                .hasSize(100)
                .allSatisfy(
                        product -> {
                            assertThat(product.price()).isPositive();
                            assertThat(product.imageUrl())
                                    .startsWith("https://picsum.photos/seed/")
                                    .endsWith("/300/200");
                            assertThat(product.productCode())
                                    .matches("PROD[0-9a-f]{64}_[0-9]{1,4}");
                        });
    }

    @Test
    void shouldGenerateRequestedBatchSize() {
        given(productService.saveProduct(any(ProductRequest.class)))
                .willAnswer(
                        invocation -> {
                            ProductRequest request = invocation.getArgument(0);
                            return Mono.just(
                                    new ProductResponse(
                                            1L,
                                            request.productCode(),
                                            request.productName(),
                                            request.description(),
                                            null,
                                            request.price(),
                                            true));
                        });

        StepVerifier.create(generatorService.generateAndSaveProducts("test-batch-456", 5))
                .expectSubscription()
                .expectNext(Boolean.TRUE)
                .verifyComplete();

        then(productService).should(times(5)).saveProduct(productCaptor.capture());
        assertThat(productCaptor.getAllValues())
                .extracting(ProductRequest::productCode)
                .containsExactlyElementsOf(
                        generatorService
                                .generateProducts("test-batch-456", 5)
                                .map(ProductRequest::productCode)
                                .collectList()
                                .block());
    }

    @Test
    void shouldScopeCodesByKeyAndReuseCodesOnRetry() {
        List<ProductRequest> first =
                generatorService.generateProducts("batch-A", 3).collectList().block();
        assertThat(generatorService.generateProducts("batch-A", 3).collectList().block())
                .containsExactlyElementsOf(first);
        assertThat(
                        generatorService
                                .generateProducts("batch-B", 3)
                                .map(ProductRequest::productCode)
                                .collectList()
                                .block())
                .doesNotContainAnyElementsOf(
                        first.stream().map(ProductRequest::productCode).toList());
    }

    @Test
    void shouldKeepMaximumBatchCodesUniqueBoundedAndUrlSafeForArbitraryKeys() {
        List<ProductRequest> products =
                generatorService
                        .generateProducts("日本語 /?#".repeat(1000), 10_000)
                        .collectList()
                        .block();
        assertThat(products).hasSize(10_000);
        assertThat(products).extracting(ProductRequest::productCode).doesNotHaveDuplicates();
        assertThat(products)
                .allSatisfy(
                        product -> {
                            assertThat(product.productCode())
                                    .matches("PROD[0-9a-f]{64}_[0-9]{1,4}")
                                    .hasSizeLessThanOrEqualTo(73);
                            assertThat(product.imageUrl())
                                    .isEqualTo(
                                            "https://picsum.photos/seed/"
                                                    + product.productCode()
                                                    + "/300/200");
                        });
        assertThat(products.getLast().productCode()).endsWith("_9999");
    }
}
