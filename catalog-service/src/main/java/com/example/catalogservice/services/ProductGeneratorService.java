/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.catalogservice.services;

import com.example.catalogservice.model.request.ProductRequest;
import java.math.BigDecimal;
import java.security.SecureRandom;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Dedicated service responsible for generating mock and benchmark catalog products. */
@Service
public class ProductGeneratorService {

    public static final int MAX_GENERATION_BATCH_SIZE = 10_000;
    private static final int DEFAULT_GENERATION_BATCH_SIZE = 101;
    private static final SecureRandom RAND = new SecureRandom();

    private final ProductService productService;

    public ProductGeneratorService(@Lazy ProductService productService) {
        this.productService = productService;
    }

    /**
     * Creates sample products with whole-unit prices from 1.00 through 100.00.
     *
     * @param idempotencyKey included in each product code with its zero-based batch index
     * @param batchSize number of products, from 1 through 10,000, or null for 101
     * @return true after all saves complete
     */
    @Transactional
    public Mono<Boolean> generateProducts(String idempotencyKey, Integer batchSize) {
        validateBatchSize(batchSize);
        int resolvedBatchSize = batchSize != null ? batchSize : DEFAULT_GENERATION_BATCH_SIZE;

        return Flux.range(0, resolvedBatchSize)
                .flatMap(
                        i ->
                                Mono.just(RAND.nextInt(100) + 1)
                                        .map(
                                                randomPrice ->
                                                        new ProductRequest(
                                                                "ProductCode_"
                                                                        + idempotencyKey
                                                                        + "_"
                                                                        + i,
                                                                "Gen Product " + i,
                                                                "Gen Prod Description " + i,
                                                                null,
                                                                BigDecimal.valueOf(randomPrice)
                                                                        .setScale(2))))
                .flatMap(productService::saveProduct)
                .then(Mono.just(Boolean.TRUE));
    }

    private static void validateBatchSize(Integer batchSize) {
        if (batchSize != null && (batchSize < 1 || batchSize > MAX_GENERATION_BATCH_SIZE)) {
            throw new IllegalArgumentException(
                    "batchSize must be between 1 and " + MAX_GENERATION_BATCH_SIZE);
        }
    }
}
