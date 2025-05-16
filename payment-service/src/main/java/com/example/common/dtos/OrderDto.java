/*** Licensed under MIT License Copyright (c) 2021-2025 Raja Kolli. ***/
package com.example.common.dtos;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import java.io.Serializable;
import java.util.List;

public record OrderDto(
        Long orderId,
        @Positive(message = "CustomerId should be positive") Long customerId,
        String status,
        String source,
        @NotEmpty(message = "Order without items not valid") List<OrderItemDto> items)
        implements Serializable {
    public OrderDto withSource(String source) {
        return new OrderDto(orderId(), customerId(), status(), source, items());
    }

    public OrderDto withCustomerId(long customerId) {
        return new OrderDto(orderId(), customerId, status(), source(), items());
    }

    public OrderDto withStatus(String status) {
        return new OrderDto(orderId(), customerId(), status, source(), items());
    }
}
