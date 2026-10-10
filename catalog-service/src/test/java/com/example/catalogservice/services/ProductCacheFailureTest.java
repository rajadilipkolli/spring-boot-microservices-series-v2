/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.catalogservice.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.catalogservice.entities.Product;
import com.example.catalogservice.mapper.ProductMapper;
import com.example.catalogservice.model.request.ProductRequest;
import com.example.catalogservice.model.response.InventoryResponse;
import com.example.catalogservice.model.response.ProductResponse;
import com.example.catalogservice.repositories.ProductRepository;
import io.hypersistence.tsid.TSID;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ProductCacheFailureTest {

    private final ProductRepository repository = mock(ProductRepository.class);
    private final ProductMapper mapper = mock(ProductMapper.class);
    private final InventoryServiceProxy inventory = mock(InventoryServiceProxy.class);
    private final OutboxService outbox = mock(OutboxService.class);
    private final ReactiveRedisOperations<String, Object> redis = mock();
    private final ReactiveValueOperations<String, Object> values = mock();
    private final AtomicReference<String> generation = new AtomicReference<>("initial");
    private final Map<String, Object> cache = new HashMap<>();
    private final Product product =
            new Product().setId(1L).setProductCode("P001").setProductName("Fresh");
    private ProductService service;

    @BeforeEach
    void setUp() {
        service = newService();
        when(repository.findCacheGeneration()).thenAnswer(ignored -> Mono.just(generation.get()));
        lenient()
                .when(repository.updateCacheGeneration(anyString()))
                .thenAnswer(
                        invocation ->
                                Mono.fromSupplier(
                                        () -> {
                                            generation.set(invocation.getArgument(0));
                                            return 1;
                                        }));
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString()))
                .thenAnswer(invocation -> Mono.justOrEmpty(cache.get(invocation.getArgument(0))));
        when(values.set(anyString(), any(), any(Duration.class)))
                .thenAnswer(
                        invocation -> {
                            cache.put(invocation.getArgument(0), invocation.getArgument(1));
                            return Mono.just(true);
                        });
        lenient().when(repository.findById(1L)).thenReturn(Mono.just(product));
        lenient()
                .when(repository.findByProductCodeAllIgnoreCase(anyString()))
                .thenReturn(Mono.just(product));
        when(mapper.toProductResponse(product))
                .thenAnswer(
                        ignored ->
                                new ProductResponse(
                                        1L,
                                        "P001",
                                        product.getProductName(),
                                        "Description",
                                        null,
                                        new java.math.BigDecimal("10"),
                                        false));
    }

    @Test
    void redisReadAndWriteFailuresDoNotHideDatabaseResults() {
        when(values.get(anyString()))
                .thenReturn(Mono.error(new IllegalStateException("Redis unavailable")));
        when(values.set(anyString(), any(), any(Duration.class)))
                .thenReturn(Mono.error(new IllegalStateException("Redis unavailable")));
        when(inventory.getInventoryByProductCode("P001"))
                .thenReturn(Mono.just(new InventoryResponse("P001", 5)));
        when(inventory.getInventoryByProductCodes(any())).thenReturn(Flux.empty());
        when(repository.count()).thenReturn(Mono.just(1L));
        when(repository.findAllBy(any(Pageable.class))).thenReturn(Flux.just(product));

        StepVerifier.create(service.findProductById(1L))
                .assertNext(response -> assertThat(response.productName()).isEqualTo("Fresh"))
                .verifyComplete();
        StepVerifier.create(service.findProductByProductCode("p001", false))
                .assertNext(response -> assertThat(response.productName()).isEqualTo("Fresh"))
                .verifyComplete();
        StepVerifier.create(service.findAllProducts(0, 10, "id", "asc"))
                .assertNext(response -> assertThat(response.data()).hasSize(1))
                .verifyComplete();
    }

    @Test
    void failedEvictionCannotExposeOldEntriesToAnotherNodeAfterRedisRecovers() {
        service.findProductByProductCode("P001", false).block();
        assertThat(cache).containsKey("productCode:p001:initial");
        product.setProductName("Updated");
        when(redis.delete(any(String[].class)))
                .thenReturn(Mono.error(new IllegalStateException("Redis unavailable")));

        // Saving an existing product takes the idempotent path and invalidates its caches.
        StepVerifier.create(
                        service.saveProduct(
                                new ProductRequest(
                                        "P001",
                                        "Updated",
                                        "Description",
                                        null,
                                        new java.math.BigDecimal(5))))
                .assertNext(response -> assertThat(response.productName()).isEqualTo("Updated"))
                .verifyComplete();

        assertThat(cache).containsKey("productCode:p001:initial");
        StepVerifier.create(newService().findProductByProductCode("p001", false))
                .assertNext(response -> assertThat(response.productName()).isEqualTo("Updated"))
                .verifyComplete();
    }

    @Test
    void databaseErrorsAreNotSwallowedAsCacheFailures() {
        when(repository.findByProductCodeAllIgnoreCase("P001"))
                .thenReturn(Mono.error(new IllegalArgumentException("database failure")));
        StepVerifier.create(service.findProductByProductCode("P001", false))
                .expectErrorMessage("database failure")
                .verify();
    }

    private ProductService newService() {
        return new ProductService(
                repository, mapper, inventory, outbox, redis, null, TSID.Factory.builder().build());
    }
}
