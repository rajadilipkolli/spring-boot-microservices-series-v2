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
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;

import com.example.catalogservice.entities.Product;
import com.example.catalogservice.mapper.ProductMapper;
import com.example.catalogservice.model.request.ProductRequest;
import com.example.catalogservice.model.response.ProductResponse;
import com.example.catalogservice.repositories.ProductRepository;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock private ProductMapper productMapper;

    @Mock private ProductRepository productRepository;

    @Mock private OutboxService outboxService;

    @Mock private InventoryServiceProxy inventoryServiceProxy;

    private ProductService productService;

    @Captor private ArgumentCaptor<ProductRequest> productCaptor;

    @BeforeEach
    void setUp() {
        ProductService spy =
                spy(
                        new ProductService(
                                productRepository,
                                productMapper,
                                inventoryServiceProxy,
                                outboxService,
                                null));
        ProxyFactory proxyFactory = new ProxyFactory(spy);
        ProductService proxy = (ProductService) proxyFactory.getProxy();
        ReflectionTestUtils.setField(spy, "self", proxy);
        productService = spy;
    }

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
                                    .setPrice(randomPrice);
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
        given(productRepository.findByProductCodeAllIgnoreCase(any(String.class)))
                .willReturn(Mono.empty());

        // Stubbing productRepository.save()
        given(productRepository.save(any(Product.class))).willReturn(Mono.just(new Product()));

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
                .allSatisfy(product -> assertThat(product.price()).isBetween(1.0, 100.0));
    }

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
                                    .setPrice(request.price().intValue());
                        });
        given(productMapper.toProductResponse(any(Product.class)))
                .willReturn(new ProductResponse(1L, "code", "name", "description", null, 1, true));
        given(outboxService.createOutboxEvent(any(), any(), any(), any())).willReturn(Mono.empty());
        given(productRepository.findByProductCodeAllIgnoreCase(any(String.class)))
                .willReturn(Mono.empty());
        given(productRepository.save(any(Product.class))).willReturn(Mono.just(new Product()));

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
    }

    @Test
    void saveProduct_whenEmpty_shouldUseProxyAndCacheEvict() throws Exception {
        ProductRequest request = new ProductRequest("P001", "name", "desc", null, 10.0);
        Product product = new Product().setId(1L).setProductCode("P001");
        ProductResponse response =
                new ProductResponse(1L, "P001", "name", "desc", null, 10.0, true);

        given(productRepository.findByProductCodeAllIgnoreCase("P001")).willReturn(Mono.empty());
        given(productMapper.toEntity(request)).willReturn(product);
        given(productRepository.save(product)).willReturn(Mono.just(product));
        given(outboxService.createOutboxEvent(any(), any(), any(), any())).willReturn(Mono.empty());
        given(productMapper.toProductResponse(product)).willReturn(response);

        StepVerifier.create(productService.saveProduct(request))
                .expectNext(response)
                .verifyComplete();

        // Verify the proxy routed to the self method
        then(productService).should().createAndSaveProduct(request);

        // Verify annotations for Transactional and CacheEvict are present to confirm cache
        // invalidation logic
        Method method = ProductService.class.getMethod("saveProduct", ProductRequest.class);
        assertThat(method.isAnnotationPresent(Transactional.class)).isTrue();

        CacheEvict cacheEvict = method.getAnnotation(CacheEvict.class);
        assertThat(cacheEvict).isNotNull();
        assertThat(cacheEvict.cacheNames()).contains("products");
        assertThat(cacheEvict.allEntries()).isTrue();

        // Verify createAndSaveProduct is transactional
        Method createMethod =
                ProductService.class.getMethod("createAndSaveProduct", ProductRequest.class);
        assertThat(createMethod.isAnnotationPresent(Transactional.class)).isTrue();
    }
}
