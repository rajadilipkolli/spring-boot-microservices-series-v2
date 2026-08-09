package com.example.retailstore.webapp.clients.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public record OrderItemRequest(
        @NotBlank(message = "Product code cannot be blank") String productCode,
        @Positive(message = "Quantity must be positive") Integer quantity,
        @PositiveOrZero(message = "Price cannot be negative") BigDecimal price) {}
