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

    Mono<Long> countDistinctByProductCodeAllIgnoreCaseInAndDeletedFalse(
            List<String> productCodeList);

    Mono<Long> countByProductCodeAllIgnoreCaseAndDeletedFalse(String productCode);

    Mono<Product> findByIdAndDeletedFalse(Long id);

    Mono<Product> findByProductCodeAllIgnoreCaseAndDeletedFalse(String productCode);

    Mono<Product> findByProductCodeAllIgnoreCaseAndDeletedTrue(String productCode);

    Mono<Void> deleteByProductCodeAllIgnoreCase(String productCode);

    Mono<Long> countByDeletedFalse();

    Flux<Product> findAllByDeletedFalse(Pageable pageable);

    // Search by term (in product name or description)
    Flux<Product>
            findByProductNameContainingIgnoreCaseAndDeletedFalseOrDescriptionContainingIgnoreCaseAndDeletedFalse(
                    String productName, String description, Pageable pageable);

    // Search by price range
    /**
     * Returns products within the inclusive unit-price bounds using the requested page and sort.
     * Database errors propagate through the returned Flux.
     */
    Flux<Product> findByPriceBetweenAndDeletedFalse(
            BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable);

    // Search by both term and price range
    /**
     * Returns case-insensitive name matches, or description matches within the inclusive unit-price
     * bounds, using the requested page and sort. The price bounds apply only to description
     * matches. Database errors propagate through the returned Flux.
     */
    Flux<Product>
            findByProductNameContainingIgnoreCaseAndDeletedFalseOrDescriptionContainingIgnoreCaseAndPriceBetweenAndDeletedFalse(
                    String productName,
                    String description,
                    BigDecimal minPrice,
                    BigDecimal maxPrice,
                    Pageable pageable);
}
