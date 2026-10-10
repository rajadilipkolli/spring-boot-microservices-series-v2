/*** Licensed under MIT License Copyright (c) 2026 Raja Kolli. ***/
package com.example.paymentservice.model.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING) Long orderId,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Long customerId,
        String status,
        String source,
        Address deliveryAddress,
        LocalDateTime createdDate,
        BigDecimal totalPrice,
        List<OrderItemResponse> items) {

    public record Address(
            String addressLine1,
            String addressLine2,
            String city,
            String state,
            String zipCode,
            String country) {}

    public record OrderItemResponse(
            @JsonFormat(shape = JsonFormat.Shape.STRING) Long itemId,
            String productId,
            int quantity,
            BigDecimal productPrice,
            BigDecimal price) {}
}
