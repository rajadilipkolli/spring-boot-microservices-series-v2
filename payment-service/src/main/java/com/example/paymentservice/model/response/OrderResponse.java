/*** Licensed under MIT License Copyright (c) 2026 Raja Kolli. ***/
package com.example.paymentservice.model.response;

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
        @Schema(description = "Total order price", example = "199.99") BigDecimal totalPrice,
        @Schema(description = "List of order items") List<OrderItemResponse> items) {

    @Schema(description = "Delivery address")
    public record Address(
            @Schema(description = "Address line 1", example = "123 Main St") String addressLine1,
            @Schema(description = "Address line 2", example = "Suite 100") String addressLine2,
            @Schema(description = "City", example = "San Francisco") String city,
            @Schema(description = "State", example = "CA") String state,
            @Schema(description = "Zip code", example = "94105") String zipCode,
            @Schema(description = "Country", example = "USA") String country) {}

    @Schema(description = "Order line item details response")
    public record OrderItemResponse(
            @Schema(type = "string", description = "Item ID", example = "1")
                    @JsonFormat(shape = JsonFormat.Shape.STRING)
                    Long itemId,
            @Schema(description = "Product code/identifier", example = "P100") String productId,
            @Schema(description = "Ordered quantity", example = "2") int quantity,
            @Schema(description = "Unit product price", example = "99.99") BigDecimal productPrice,
            @Schema(description = "Total line item price", example = "199.98") BigDecimal price) {}
}
