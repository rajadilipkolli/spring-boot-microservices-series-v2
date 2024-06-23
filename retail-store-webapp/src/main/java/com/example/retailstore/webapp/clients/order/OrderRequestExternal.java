/***
 * <p>
 * Licensed under MIT License Copyright (c) 2023-2024 Raja Kolli.
 * </p>
 ***/
package com.example.retailstore.webapp.clients.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record OrderRequestExternal(
        @Positive(message = "CustomerId should be positive") Long customerId,
        @NotEmpty(message = "Order without items not valid") List<OrderItemExternal> items,
        @Valid Address deliveryAddress) {}
