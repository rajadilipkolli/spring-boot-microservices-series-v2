/***
<p>
    Licensed under MIT License Copyright (c) 2023-2024 Raja Kolli.
</p>
***/

package com.example.catalogservice.model.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;

public record ProductResponse(
        Long id,
        String code,
        String productName,
        String description,
        String imageUrl,
        @JsonFormat(shape = JsonFormat.Shape.NUMBER_FLOAT, pattern = "0.00") double price,
        boolean inStock) {

    @JsonIgnore
    public ProductResponse withInStock(final boolean inStock) {
        return this.inStock == inStock
                ? this
                : new ProductResponse(
                        this.id,
                        this.code,
                        this.productName,
                        this.description,
                        this.imageUrl,
                        this.price,
                        inStock);
    }
}
