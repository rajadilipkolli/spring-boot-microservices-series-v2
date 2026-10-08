/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.catalogservice.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import com.example.catalogservice.common.AbstractIntegrationTest;
import com.example.catalogservice.entities.Product;
import com.example.catalogservice.exception.ProductNotFoundException;
import com.example.catalogservice.model.request.ProductRequest;
import com.example.catalogservice.model.response.InventoryResponse;
import com.example.catalogservice.model.response.ProductResponse;
import io.hypersistence.tsid.TSID;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ProductServiceCachingIntegrationTest extends AbstractIntegrationTest {

    @MockitoBean private InventoryServiceProxy inventoryServiceProxy;

    @BeforeEach
    void setUp() {
        redisOps.execute(connection -> connection.serverCommands().flushAll()).blockFirst();
        productRepository.deleteByProductCodeAllIgnoreCase("P001").block();
        productRepository.deleteByProductCodeAllIgnoreCase("RENAMED").block();
        productRepository
                .save(
                        new Product()
                                .setId(TSID.fast().toLong())
                                .setNew(true)
                                .setProductCode("P001")
                                .setProductName("Cache test product")
                                .setDescription("Deterministic fixture")
                                .setPrice(10.0))
                .block();
        when(inventoryServiceProxy.getInventoryByProductCodes(anyList())).thenReturn(Flux.empty());
    }

    @Test
    void testFindAllProductsIsCached() {
        StepVerifier.create(productService.findAllProducts(0, 10, "id", "asc"))
                .consumeNextWith(result -> assertThat(result.data()).isNotEmpty())
                .verifyComplete();

        StepVerifier.create(redisOps.hasKey(cacheKey("products:0_10_id_asc")))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void testFindProductByCodeIsCachedAndServedFromCache() {
        ProductResponse initialResponse =
                productService.findProductByProductCode("P001", false).block();
        assertThat(initialResponse).isNotNull();
        String expectedCacheKey = cacheKey("productCode:p001");
        assertThat(redisOps.hasKey(expectedCacheKey).block()).isTrue();

        Product productInDb = productRepository.findByProductCodeAllIgnoreCase("P001").block();
        productInDb.setProductName("Bypassed Name Update");
        productInDb = productRepository.save(productInDb).block();

        StepVerifier.create(productService.findProductByProductCode("p001", false))
                .consumeNextWith(
                        response ->
                                assertThat(response.productName())
                                        .isEqualTo(initialResponse.productName()))
                .verifyComplete();

        productService
                .updateProduct(
                        new ProductRequest("RENAMED", "Proper Update Name", "Updated", null, 20.0),
                        productInDb)
                .block();
        assertThat(redisOps.hasKey(expectedCacheKey).block()).isFalse();
        StepVerifier.create(productService.findProductByProductCode("renamed", false))
                .consumeNextWith(
                        response ->
                                assertThat(response.productName()).isEqualTo("Proper Update Name"))
                .verifyComplete();
        StepVerifier.create(productService.findProductByProductCode("P001", false))
                .expectError(ProductNotFoundException.class)
                .verify();
    }

    @Test
    void idCacheExpiresAndMutationsChangePageGeneration() {
        Product product = productRepository.findByProductCodeAllIgnoreCase("P001").block();
        when(inventoryServiceProxy.getInventoryByProductCode("P001"))
                .thenReturn(Mono.just(new InventoryResponse("P001", 5)));
        productService.findProductById(product.getId()).block();
        assertThat(redisOps.getExpire(cacheKey("product:" + product.getId())).block())
                .isPositive()
                .isLessThanOrEqualTo(Duration.ofMinutes(5));

        productService.findAllProducts(0, 10, "id", "asc").block();
        String beforeUpdate = cacheKey("products:0_10_id_asc");
        productService
                .updateProduct(
                        new ProductRequest("P001", "Updated name", "Updated", null, 20.0), product)
                .block();
        assertThat(cacheKey("products:0_10_id_asc")).isNotEqualTo(beforeUpdate);

        String beforeDelete = productRepository.findCacheGeneration().block();
        productService.deleteProductById(product.getId()).block();
        assertThat(productRepository.findCacheGeneration().block()).isNotEqualTo(beforeDelete);

        String beforeSave = productRepository.findCacheGeneration().block();
        productService
                .saveProduct(new ProductRequest("P001", "Created", "Created", null, 20.0))
                .block();
        assertThat(productRepository.findCacheGeneration().block()).isNotEqualTo(beforeSave);

        String beforeGenerate = productRepository.findCacheGeneration().block();
        productService.generateProducts("cache-test", 1).block();
        assertThat(productRepository.findCacheGeneration().block()).isNotEqualTo(beforeGenerate);
        productRepository.deleteByProductCodeAllIgnoreCase("ProductCode_cache-test_0").block();
    }

    private String cacheKey(String key) {
        return key + ":" + productRepository.findCacheGeneration().block();
    }
}
