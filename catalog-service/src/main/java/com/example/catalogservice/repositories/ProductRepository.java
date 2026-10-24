/***
<p>
    Licensed under MIT License Copyright (c) 2021-2026 Raja Kolli.
</p>
***/

package com.example.catalogservice.repositories;

import com.example.catalogservice.entities.Product;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.data.repository.reactive.ReactiveSortingRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ProductRepository
        extends ReactiveCrudRepository<Product, Long>, ReactiveSortingRepository<Product, Long> {

    /**
     * Counts distinct active product codes matching the supplied codes case-insensitively. Database
     * errors propagate through the returned Mono.
     */
    Mono<Long> countDistinctByProductCodeAllIgnoreCaseInAndDeletedFalse(
            List<String> productCodeList);

    /** Counts active products with the given case-insensitive code; database errors propagate. */
    Mono<Long> countByProductCodeAllIgnoreCaseAndDeletedFalse(String productCode);

    /**
     * Finds an active product by ID, emitting nothing if absent or deleted. Database errors
     * propagate through the returned Mono.
     */
    Mono<Product> findByIdAndDeletedFalse(Long id);

    /**
     * Finds an active product by case-insensitive code, emitting nothing if no active match exists.
     * Database errors propagate through the returned Mono.
     */
    Mono<Product> findByProductCodeAllIgnoreCaseAndDeletedFalse(String productCode);

    /**
     * Finds a deleted product by case-insensitive code for restoration, emitting nothing if no
     * deleted match exists. Database errors propagate through the returned Mono.
     */
    Mono<Product> findByProductCodeAllIgnoreCaseAndDeletedTrue(String productCode);

    Mono<Void> deleteByProductCodeAllIgnoreCase(String productCode);

    /** Counts active products, excluding retained deleted rows; database errors propagate. */
    Mono<Long> countByDeletedFalse();

    /** Returns active products using the requested page and sort; database errors propagate. */
    Flux<Product> findAllByDeletedFalse(Pageable pageable);

    // Search by term (in product name or description)
    /**
     * Returns active products whose name contains productName or whose description contains
     * description, ignoring case and using the requested page and sort. Database errors propagate
     * through the returned Flux.
     */
    Flux<Product>
            findByProductNameContainingIgnoreCaseAndDeletedFalseOrDescriptionContainingIgnoreCaseAndDeletedFalse(
                    String productName, String description, Pageable pageable);

    // Search by price range
    /**
     * Returns active products within the inclusive unit-price bounds using the requested page and
     * sort. Database errors propagate through the returned Flux.
     */
    Flux<Product> findByPriceBetweenAndDeletedFalse(
            BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable);

    // Search by both term and price range
    /**
     * Returns case-insensitive name matches, or description matches within the inclusive unit-price
     * bounds, using the requested page and sort. Deleted products are excluded from both branches.
     * The price bounds apply only to description matches. Database errors propagate through the
     * returned Flux.
     */
    Flux<Product>
            findByProductNameContainingIgnoreCaseAndDeletedFalseOrDescriptionContainingIgnoreCaseAndPriceBetweenAndDeletedFalse(
                    String productName,
                    String description,
                    BigDecimal minPrice,
                    BigDecimal maxPrice,
                    Pageable pageable);
}
