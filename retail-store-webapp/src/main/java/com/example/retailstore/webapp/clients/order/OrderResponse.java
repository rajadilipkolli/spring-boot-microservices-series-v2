package com.example.retailstore.webapp.clients.order;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(
        Long orderId,
        Long customerId,
        String status,
        String source,
        LocalDateTime createdDate,
        @JsonFormat(shape = JsonFormat.Shape.NUMBER_FLOAT, pattern = "0.00") BigDecimal totalPrice,
        List<OrderItemResponse> items) {}
