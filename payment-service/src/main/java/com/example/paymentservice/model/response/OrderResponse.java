/*** Licensed under MIT License Copyright (c) 2026 Raja Kolli. ***/
package com.example.paymentservice.model.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(
        Long orderId,
        Long customerId,
        String status,
        String source,
        Address deliveryAddress,
        LocalDateTime createdDate,
        BigDecimal totalPrice,
        List<OrderItemResponse> items) {

    public record Address(
            String street, String city, String state, String zipCode, String country) {}

    public record OrderItemResponse(
            Long itemId, String productId, int quantity, BigDecimal price) {}
}
