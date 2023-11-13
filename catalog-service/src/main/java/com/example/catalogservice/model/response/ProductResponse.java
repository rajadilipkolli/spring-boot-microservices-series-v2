/***
<p>
    Licensed under MIT License Copyright (c) 2023 Raja Kolli.
</p>
***/

package com.example.catalogservice.model.response;

public record ProductResponse(
        Long id,
        String code,
        String productName,
        String description,
        double price,
        boolean inStock) {

    public ProductResponse withInStock(final boolean inStock) {
        return this.inStock == inStock
                ? this
                : new ProductResponse(
                        this.id,
                        this.code,
                        this.productName,
                        this.description,
                        this.price,
                        inStock);
    }
}
