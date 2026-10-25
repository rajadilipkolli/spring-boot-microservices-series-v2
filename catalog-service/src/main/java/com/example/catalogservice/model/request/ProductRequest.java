/***
<p>
    Licensed under MIT License Copyright (c) 2021-2025 Raja Kolli.
</p>
***/

package com.example.catalogservice.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

@Schema(description = "Product creation or update request")
public record ProductRequest(
        @Schema(description = "Product code", example = "P100")
                @NotBlank(message = "Product code can't be blank") String productCode,
        @Schema(description = "Product name", example = "Product One") String productName,
        @Schema(description = "Product description", example = "Description for Product One")
                String description,
        @Schema(description = "Product image URL", example = "https://example.com/image.png")
                String imageUrl,
        @Schema(description = "Product price", example = "99.99")
                @Positive @Digits(integer = 17, fraction = 2) BigDecimal price) {}
