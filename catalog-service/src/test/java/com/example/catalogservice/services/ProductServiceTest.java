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
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;

import com.example.catalogservice.entities.Product;
import com.example.catalogservice.mapper.ProductMapper;
import com.example.catalogservice.model.request.ProductRequest;
import com.example.catalogservice.model.response.ProductResponse;
import com.example.catalogservice.repositories.ProductCacheGenerationRepository;
import com.example.catalogservice.repositories.ProductRepository;
import io.hypersistence.tsid.TSID;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock private ProductMapper productMapper;

    @Mock private ProductRepository productRepository;

    @Mock private ProductCacheGenerationRepository productCacheGenerationRepository;

    @Mock private OutboxService outboxService;

    @Mock private ReactiveRedisOperations<String, Object> redisOps;

    @Mock private ProductGeneratorService productGeneratorService;

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
                                productGeneratorService,
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
     * Verifies default batch generation saves new products with prices in range and creates outbox
     * events.
     */
    @Test
    void testGenerateProducts() {
        // Stubbing productMapper.toEntity()
        given(productMapper.toEntity(any(ProductRequest.class)))
                .willAnswer(
                        invocation -> {
                            ProductRequest request = invocation.getArgument(0);
                            int randomPrice = ThreadLocalRandom.current().nextInt(1, 101);
                            return new Product()
                                    .setId(1L)
                                    .setProductCode(request.productCode())
                                    .setProductName(request.productName())
                                    .setDescription(request.description())
                                    .setPrice(BigDecimal.valueOf(randomPrice));
                        });

        // Stubbing productMapper.toProductResponse()
        given(productMapper.toProductResponse(any(Product.class)))
                .willAnswer(
                        invocationOnMock -> {
                            Product product = invocationOnMock.getArgument(0);
                            return new ProductResponse(
                                    product.getId(),
                                    product.getProductCode(),
                                    product.getProductName(),
                                    product.getDescription(),
                                    null,
                                    product.getPrice(),
                                    true);
                        });

        given(outboxService.createOutboxEvent(any(), any(), any(), any())).willReturn(Mono.empty());

        // Mock the repository findByProductCodeAllIgnoreCase method to return empty Mono
        // This is needed for the idempotency check in saveProduct
        given(productRepository.findByProductCodeAllIgnoreCaseAndDeletedFalse(any(String.class)))
                .willReturn(Mono.empty());

        // Stubbing productRepository.save()
        given(productRepository.save(any(Product.class)))
                .willAnswer(invocation -> Mono.just(invocation.getArgument(0, Product.class)));

        given(productGeneratorService.generateProducts("test-batch-123", null))
                .willAnswer(
                        invocation -> {
                            String idempotencyKey = invocation.getArgument(0);
                            return Flux.range(0, 101)
                                    .map(
                                            i ->
                                                    new ProductRequest(
                                                            "ProductCode_"
                                                                    + idempotencyKey
                                                                    + "_"
                                                                    + i,
                                                            "ProductName_"
                                                                    + idempotencyKey
                                                                    + "_"
                                                                    + i,
                                                            "Description for product "
                                                                    + idempotencyKey,
                                                            null,
                                                            BigDecimal.valueOf(
                                                                    ThreadLocalRandom.current()
                                                                            .nextInt(1, 101))));
                        });

        // Use StepVerifier to test the method
        StepVerifier.create(productService.generateProducts("test-batch-123", null))
                .expectSubscription()
                .expectNext(Boolean.TRUE)
                .verifyComplete();

        then(productMapper).should(atLeastOnce()).toEntity(productCaptor.capture());
        then(outboxService).should(atLeastOnce()).createOutboxEvent(any(), any(), any(), any());

        // Assert that each product's price is within the expected range
        List<ProductRequest> capturedProducts = productCaptor.getAllValues();
        assertThat(capturedProducts)
                .isNotEmpty()
                .allSatisfy(
                        product ->
                                assertThat(product.price())
                                        .isBetween(BigDecimal.ONE, BigDecimal.valueOf(100.0)));

        then(productRepository).should(atLeastOnce()).save(productEntityCaptor.capture());
        assertThat(productEntityCaptor.getAllValues())
                .allSatisfy(
                        product -> {
                            assertThat(product.getId()).isNotNull();
                            assertThat(product.isNew()).isTrue();
                        });
    }

    /**
     * Verifies an explicit batch size produces the requested number of new products with distinct
     * codes.
     */
    @Test
    void shouldGenerateRequestedBatchSize() {
        given(productMapper.toEntity(any(ProductRequest.class)))
                .willAnswer(
                        invocation -> {
                            ProductRequest request = invocation.getArgument(0);
                            return new Product()
                                    .setProductCode(request.productCode())
                                    .setProductName(request.productName())
                                    .setDescription(request.description())
                                    .setPrice(request.price());
                        });
        given(productMapper.toProductResponse(any(Product.class)))
                .willReturn(
                        new ProductResponse(
                                1L,
                                "code",
                                "name",
                                "description",
                                null,
                                BigDecimal.valueOf(6.00),
                                true));
        given(outboxService.createOutboxEvent(any(), any(), any(), any())).willReturn(Mono.empty());
        given(productRepository.findByProductCodeAllIgnoreCaseAndDeletedFalse(any(String.class)))
                .willReturn(Mono.empty());
        given(productRepository.save(any(Product.class)))
                .willAnswer(invocation -> Mono.just(invocation.getArgument(0, Product.class)));

        given(productGeneratorService.generateProducts("test-batch-456", 5))
                .willAnswer(
                        invocation -> {
                            String idempotencyKey = invocation.getArgument(0);
                            return Flux.range(0, 5)
                                    .map(
                                            i ->
                                                    new ProductRequest(
                                                            "ProductCode_"
                                                                    + idempotencyKey
                                                                    + "_"
                                                                    + i,
                                                            "ProductName_"
                                                                    + idempotencyKey
                                                                    + "_"
                                                                    + i,
                                                            "Description for product "
                                                                    + idempotencyKey
                                                                    + "_"
                                                                    + i,
                                                            null,
                                                            BigDecimal.valueOf(
                                                                    ThreadLocalRandom.current()
                                                                            .nextInt(1, 101))));
                        });

        StepVerifier.create(productService.generateProducts("test-batch-456", 5))
                .expectSubscription()
                .expectNext(Boolean.TRUE)
                .verifyComplete();

        then(productMapper).should(times(5)).toEntity(productCaptor.capture());
        assertThat(productCaptor.getAllValues())
                .extracting(ProductRequest::productCode)
                .containsExactly(
                        "ProductCode_test-batch-456_0",
                        "ProductCode_test-batch-456_1",
                        "ProductCode_test-batch-456_2",
                        "ProductCode_test-batch-456_3",
                        "ProductCode_test-batch-456_4");

        then(productRepository).should(times(5)).save(productEntityCaptor.capture());
        assertThat(productEntityCaptor.getAllValues())
                .allSatisfy(
                        product -> {
                            assertThat(product.getId()).isNotNull();
                            assertThat(product.isNew()).isTrue();
                        });
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
}
