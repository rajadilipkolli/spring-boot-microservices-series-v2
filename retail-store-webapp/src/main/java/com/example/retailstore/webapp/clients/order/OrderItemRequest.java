package com.example.retailstore.webapp.clients.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record OrderItemRequest(
        @NotBlank(message = "Product code cannot be blank") String productCode,

        @NotNull(message = "Quantity cannot be null") @Positive(message = "Quantity must be positive")
        Integer quantity,

        @NotNull(message = "Price cannot be null")
        @jakarta.validation.constraints.DecimalMin("0.01")
        @jakarta.validation.constraints.Digits(integer = 17, fraction = 2)
        BigDecimal price) {}
