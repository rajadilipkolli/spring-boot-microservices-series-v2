/***
<p>
    Licensed under MIT License Copyright (c) 2023-2025 Raja Kolli.
</p>
***/

package com.example.orderservice.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

@Schema(description = "Order line item request")
public record OrderItemRequest(
        @Schema(description = "Product code", example = "P100")
                @NotBlank(message = "Product code must be provided")
                String productCode,
        @Schema(description = "Quantity ordered", example = "2")
                @Positive(message = "Quantity should be greater than zero")
                int quantity,
        @Schema(description = "Unit product price", example = "99.99")
                @NotNull
                @DecimalMin(
                        value = "0.01",
                        inclusive = true,
                        message = "Price should be greater than zero")
                @Digits(integer = 17, fraction = 2)
                BigDecimal productPrice) {}
