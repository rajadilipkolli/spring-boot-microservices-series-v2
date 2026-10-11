/***
<p>
    Licensed under MIT License Copyright (c) 2024-2026 Raja Kolli.
</p>
***/

package com.example.inventoryservice.inventory.web.service;

import com.example.inventoryservice.inventory.domain.models.OrderDto;
import java.math.BigDecimal;
import java.util.List;

class MockTestData {
    public static OrderDto getOrderDto(String source) {
        OrderDto.OrderItemDto orderItemDto =
                new OrderDto.OrderItemDto(1L, "JUNIT_000", 10, BigDecimal.TEN);
        return new OrderDto(151L, 1001L, "NEW", source, List.of(orderItemDto));
    }
}
