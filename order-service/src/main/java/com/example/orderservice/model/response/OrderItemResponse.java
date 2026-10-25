/***
<p>
    Licensed under MIT License Copyright (c) 2023 Raja Kolli.
</p>
***/

package com.example.orderservice.model.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "Order line item details response")
public record OrderItemResponse(
        @Schema(type = "string", description = "Item ID", example = "1")
                @JsonFormat(shape = JsonFormat.Shape.STRING)
                Long itemId,
        @Schema(description = "Product code/identifier", example = "P100") String productId,
        @Schema(description = "Ordered quantity", example = "2") int quantity,
        @Schema(description = "Unit product price", example = "99.99")
                @JsonFormat(shape = JsonFormat.Shape.NUMBER_FLOAT, pattern = "0.00")
                BigDecimal productPrice,
        @Schema(description = "Total line item price", example = "199.98")
                @JsonFormat(shape = JsonFormat.Shape.NUMBER_FLOAT, pattern = "0.00")
                BigDecimal price) {}
