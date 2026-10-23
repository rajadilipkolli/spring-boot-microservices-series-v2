/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.catalogservice.services;

import com.example.catalogservice.model.response.InventoryResponse;
import com.example.catalogservice.model.response.ProductResponse;
import com.example.common.dto.PagedResult;
import java.util.Collections;
import java.util.List;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Dedicated service component responsible for enriching product responses with real-time stock
 * availability retrieved from inventory-service.
 */
@Component
public class ProductAvailabilityEnricher {

    private final InventoryServiceProxy inventoryServiceProxy;

    public ProductAvailabilityEnricher(InventoryServiceProxy inventoryServiceProxy) {
        this.inventoryServiceProxy = inventoryServiceProxy;
    }

    /**
     * Sets stock availability from positive inventory quantities, treating missing entries as out
     * of stock. An empty product stream yields an empty page with total zero without querying
     * inventory. Product stream and unhandled inventory errors propagate.
     *
     * @param productResponseFlux products to enrich, preserving their order
     * @param pageable pagination details
     * @param totalCount total element count, or null to use the collected product count
     * @return the page with updated {@code inStock} flags
     */
    public Mono<PagedResult<ProductResponse>> enrichWithAvailability(
            Flux<ProductResponse> productResponseFlux, Pageable pageable, Long totalCount) {
        return productResponseFlux
                .collectList()
                .flatMap(
                        productResponseList -> {
                            if (productResponseList.isEmpty()) {
                                return Mono.just(
                                        new PagedResult<>(
                                                new PageImpl<>(
                                                        Collections.emptyList(), pageable, 0)));
                            }

                            List<String> productCodes =
                                    productResponseList.stream()
                                            .map(ProductResponse::productCode)
                                            .toList();

                            return inventoryServiceProxy
                                    .getInventoryByProductCodes(productCodes)
                                    .collectMap(
                                            InventoryResponse::productCode,
                                            InventoryResponse::availableQuantity)
                                    .map(
                                            inventoriesMap ->
                                                    productResponseList.stream()
                                                            .map(
                                                                    product ->
                                                                            product.withInStock(
                                                                                    inventoriesMap
                                                                                                    .getOrDefault(
                                                                                                            product
                                                                                                                    .productCode(),
                                                                                                            0)
                                                                                            > 0))
                                                            .toList())
                                    .map(
                                            updatedList ->
                                                    new PagedResult<>(
                                                            new PageImpl<>(
                                                                    updatedList,
                                                                    pageable,
                                                                    totalCount != null
                                                                            ? totalCount
                                                                            : productResponseList
                                                                                    .size())));
                        });
    }

    /**
     * Sets a product's stock availability from a positive inventory quantity. An empty inventory
     * response or a zero-quantity fallback marks it out of stock; unhandled inventory errors
     * propagate.
     *
     * @param productResponse product response to enrich
     * @return the product with its updated {@code inStock} flag
     */
    public Mono<ProductResponse> enrichWithAvailability(ProductResponse productResponse) {
        return inventoryServiceProxy
                .getInventoryByProductCode(productResponse.productCode())
                .map(inventory -> productResponse.withInStock(inventory.availableQuantity() > 0))
                .defaultIfEmpty(productResponse.withInStock(false));
    }
}
