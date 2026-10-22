/***
<p>
    Licensed under MIT License Copyright (c) 2021-2025 Raja Kolli.
</p>
***/

package com.example.catalogservice.repositories;

import com.example.catalogservice.entities.Product;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.data.repository.reactive.ReactiveSortingRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ProductRepository
        extends ReactiveCrudRepository<Product, Long>, ReactiveSortingRepository<Product, Long> {

    /**
     * Reads the shared generation used to select product cache entries.
     *
     * @return the generation, or an empty Mono if row 1 is missing; database errors propagate
     */
    @Query("SELECT generation FROM product_cache_generation WHERE id = 1")
    Mono<String> findCacheGeneration();

    /**
     * Replaces the shared product cache generation without inserting a missing row.
     *
     * @param generation the token to use for subsequent cache lookups
     * @return the number of updated rows, zero if row 1 is missing; database errors propagate
     */
    @Modifying
    @Query("UPDATE product_cache_generation SET generation = :generation WHERE id = 1")
    Mono<Integer> updateCacheGeneration(String generation);

    Mono<Long> countDistinctByProductCodeAllIgnoreCaseIn(List<String> productCodeList);

    Mono<Long> countByProductCodeAllIgnoreCase(String productCode);

    Mono<Product> findByProductCodeAllIgnoreCase(String productCode);

    Mono<Void> deleteByProductCodeAllIgnoreCase(String productCode);

    Flux<Product> findAllBy(Pageable pageable);

    // Search by term (in product name or description)
    Flux<Product> findByProductNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(
            String productName, String description, Pageable pageable);

    // Search by price range
    /**
     * Returns products within the inclusive unit-price bounds using the requested page and sort.
     * Database errors propagate through the returned Flux.
     */
    Flux<Product> findByPriceBetween(BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable);

    // Search by both term and price range
    /**
     * Returns case-insensitive name matches, or description matches within the inclusive unit-price
     * bounds, using the requested page and sort. The price bounds apply only to description
     * matches. Database errors propagate through the returned Flux.
     */
    Flux<Product>
            findByProductNameContainingIgnoreCaseOrDescriptionContainingIgnoreCaseAndPriceBetween(
                    String productName,
                    String description,
                    BigDecimal minPrice,
                    BigDecimal maxPrice,
                    Pageable pageable);
}
