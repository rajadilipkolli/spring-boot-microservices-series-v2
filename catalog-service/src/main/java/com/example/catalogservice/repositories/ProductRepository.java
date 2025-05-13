/***
<p>
    Licensed under MIT License Copyright (c) 2021-2023 Raja Kolli.
</p>
***/

package com.example.catalogservice.repositories;

import com.example.catalogservice.entities.Product;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.data.repository.reactive.ReactiveSortingRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ProductRepository
        extends ReactiveCrudRepository<Product, Long>, ReactiveSortingRepository<Product, Long> {

    Mono<Long> countDistinctByProductCodeAllIgnoreCaseIn(List<String> productCodeList);

    Mono<Product> findByProductCodeAllIgnoreCase(String productCode);

    Flux<Product> findAllBy(Pageable pageable);

    // Search by term (in product name or description)
    Flux<Product> findByProductNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(
            String productName, String description, Pageable pageable);

    // Search by price range
    Flux<Product> findByPriceBetween(double minPrice, double maxPrice, Pageable pageable);

    // Search by both term and price range
    Flux<Product>
            findByProductNameContainingIgnoreCaseOrDescriptionContainingIgnoreCaseAndPriceBetween(
                    String productName,
                    String description,
                    double minPrice,
                    double maxPrice,
                    Pageable pageable);
}
