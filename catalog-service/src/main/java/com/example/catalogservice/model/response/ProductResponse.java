/***
<p>
    Licensed under MIT License Copyright (c) 2023-2026 Raja Kolli.
</p>
***/

package com.example.catalogservice.model.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.math.BigDecimal;

@Schema(description = "Product details response")
public record ProductResponse(
        @Schema(type = "string", description = "Product ID", example = "1")
                @JsonFormat(shape = JsonFormat.Shape.STRING)
                Long id,
        @Schema(description = "Product code", example = "P100") String productCode,
        @Schema(description = "Product name", example = "Product One") String productName,
        @Schema(description = "Product description", example = "Description for Product One")
                String description,
        @Schema(description = "Product image URL", example = "https://example.com/image.png")
                String imageUrl,
        @Schema(description = "Product price", example = "99.99")
                @JsonFormat(shape = JsonFormat.Shape.NUMBER_FLOAT, pattern = "0.00")
                BigDecimal price,
        @Schema(description = "Availability in stock", example = "true") boolean inStock)
        implements Serializable {

    @JsonIgnore
    public ProductResponse withInStock(final boolean inStock) {
        return this.inStock == inStock
                ? this
                : new ProductResponse(
                        this.id,
                        this.productCode,
                        this.productName,
                        this.description,
                        this.imageUrl,
                        this.price,
                        inStock);
    }
}
