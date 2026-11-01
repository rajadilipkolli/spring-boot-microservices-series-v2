/***
<p>
    Licensed under MIT License Copyright (c) 2024-2026 Raja Kolli.
</p>
***/

package com.example.catalogservice.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.spy;

import com.example.catalogservice.entities.Product;
import com.example.catalogservice.exception.ProductNotFoundException;
import com.example.catalogservice.mapper.ProductMapper;
import com.example.catalogservice.model.request.ProductRequest;
import com.example.catalogservice.model.response.ProductResponse;
import com.example.catalogservice.repositories.ProductCacheGenerationRepository;
import com.example.catalogservice.repositories.ProductRepository;
import io.hypersistence.tsid.TSID;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock private ProductMapper productMapper;

    @Mock private ProductRepository productRepository;

    @Mock private ProductCacheGenerationRepository productCacheGenerationRepository;

    @Mock private OutboxService outboxService;

    @Mock private ReactiveRedisOperations<String, Object> redisOps;

    @Mock private ProductAvailabilityEnricher enricher;

    private ProductService productService;

    @Captor private ArgumentCaptor<ProductRequest> productCaptor;
    @Captor private ArgumentCaptor<Product> productEntityCaptor;

    @BeforeEach
    void setUp() {
        ProductService spy =
                spy(
                        new ProductService(
                                productRepository,
                                productMapper,
                                outboxService,
                                redisOps,
                                enricher,
                                null,
                                TSID.Factory.builder().withNode(1).build(),
                                productCacheGenerationRepository));
        ReflectionTestUtils.setField(spy, "self", spy);
        productService = spy;

        lenient()
                .when(productCacheGenerationRepository.findCacheGeneration())
                .thenReturn(Mono.just("initial"));
        lenient()
                .when(productCacheGenerationRepository.updateCacheGeneration(any(String.class)))
                .thenReturn(Mono.just(1));
        lenient().when(redisOps.delete(any(String[].class))).thenReturn(Mono.just(0L));
        lenient()
                .when(
                        productRepository.findByProductCodeAllIgnoreCaseAndDeletedTrue(
                                any(String.class)))
                .thenReturn(Mono.empty());
    }

    /**
     * Verifies a missing product is created through the service proxy with transactional save
     * methods.
     */
    @Test
    void saveProductWhenEmptyShouldUseProxyAndCacheEvict() throws Exception {
        {
            ProductRequest request =
                    new ProductRequest("P001", "name", "desc", null, BigDecimal.valueOf(5.00));
            Product product = new Product().setId(1L).setProductCode("P001");
            ProductResponse response =
                    new ProductResponse(
                            1L, "P001", "name", "desc", null, BigDecimal.valueOf(6.00), true);

            given(productRepository.findByProductCodeAllIgnoreCaseAndDeletedFalse("P001"))
                    .willReturn(Mono.empty());
            given(productMapper.toEntity(request)).willReturn(product);
            given(productRepository.save(any(Product.class))).willReturn(Mono.just(product));
            given(outboxService.createOutboxEvent(any(), any(), any(), any()))
                    .willReturn(Mono.empty());
            given(productMapper.toProductResponse(any(Product.class))).willReturn(response);

            StepVerifier.create(productService.saveProduct(request))
                    .expectNext(response)
                    .verifyComplete();

            // Verify the proxy routed to the self method
            then(productService).should().createAndSaveProduct(request);

            then(productRepository).should().save(productEntityCaptor.capture());
            Product capturedProduct = productEntityCaptor.getValue();
            assertThat(capturedProduct.getId()).isNotNull();
            assertThat(capturedProduct.isNew()).isTrue();

            // Verify createAndSaveProduct is transactional
            Method method = ProductService.class.getMethod("saveProduct", ProductRequest.class);
            assertThat(method.isAnnotationPresent(Transactional.class)).isTrue();

            // Verify createAndSaveProduct is transactional
            Method createMethod =
                    ProductService.class.getMethod("createAndSaveProduct", ProductRequest.class);
            assertThat(createMethod.isAnnotationPresent(Transactional.class)).isTrue();
        }
    }

    @Test
    void updateProductWhenProductNotFoundShouldThrowException() {
        Long productId = 999L;
        ProductRequest request =
                new ProductRequest("P999", "name", "desc", null, BigDecimal.valueOf(5.00));
        given(productRepository.findByIdAndDeletedFalse(productId)).willReturn(Mono.empty());

        StepVerifier.create(productService.updateProduct(productId, request))
                .expectError(ProductNotFoundException.class)
                .verify();
    }
}
