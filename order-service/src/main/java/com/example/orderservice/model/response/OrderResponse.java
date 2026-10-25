/***
<p>
    Licensed under MIT License Copyright (c) 2023-2026 Raja Kolli.
</p>
***/

package com.example.orderservice.model.response;

import com.example.orderservice.model.Address;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "Order details response")
public record OrderResponse(
        @Schema(type = "string", description = "Order ID", example = "1")
                @JsonFormat(shape = JsonFormat.Shape.STRING)
                Long orderId,
        @Schema(type = "string", description = "Customer ID", example = "10")
                @JsonFormat(shape = JsonFormat.Shape.STRING)
                Long customerId,
        @Schema(
                        description = "Order status (e.g. NEW, ACCEPTED, REJECTED, CANCELLED)",
                        example = "NEW")
                String status,
        @Schema(description = "Order source", example = "WEB") String source,
        @Schema(description = "Delivery address") Address deliveryAddress,
        @Schema(description = "Order creation timestamp", example = "2026-01-01T12:00:00")
                LocalDateTime createdDate,
        @Schema(description = "Total order price", example = "199.99")
                @JsonFormat(shape = JsonFormat.Shape.NUMBER_FLOAT, pattern = "0.00")
                BigDecimal totalPrice,
        @Schema(description = "List of order items") List<OrderItemResponse> items) {
    public static OrderResponse emptyResponse(Long id) {
        return new OrderResponse(
                id, null, "SERVICE_UNAVAILABLE", null, null, null, BigDecimal.ZERO, List.of());
    }
}
