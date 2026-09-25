package com.example.retailstore.webapp.clients.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public record OrderItemRequest(
        @NotBlank(message = "Product code cannot be blank") String productCode,

        @NotNull(message = "Quantity cannot be null") @Positive(message = "Quantity must be positive")
        Integer quantity,

        @NotNull(message = "Price cannot be null") @PositiveOrZero(message = "Price cannot be negative")
        BigDecimal price) {}
