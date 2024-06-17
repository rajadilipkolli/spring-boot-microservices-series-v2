package com.example.retailstore.webapp.clients.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import java.io.Serializable;
import java.util.List;

public record CreateOrderRequest(
        @NotEmpty(message = "Items cannot be empty.") List<OrderItemRequest> items,
        @Positive Long customerId,
        @Valid Address deliveryAddress)
        implements Serializable {}
