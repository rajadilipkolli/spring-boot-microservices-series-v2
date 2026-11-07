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
                            assertThat(product.productCode()).matches("PROD\\d{3}");
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
                .containsExactly("PROD000", "PROD001", "PROD002", "PROD003", "PROD004");
    }
}
