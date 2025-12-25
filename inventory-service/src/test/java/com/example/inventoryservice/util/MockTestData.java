/***
<p>
    Licensed under MIT License Copyright (c) 2024-2025 Raja Kolli.
</p>
***/

package com.example.inventoryservice.util;

import com.example.common.dtos.OrderDto;
import com.example.common.dtos.OrderItemDto;
import java.math.BigDecimal;
import java.util.List;

public class MockTestData {
    public static OrderDto getOrderDto(String source) {
        OrderItemDto orderItemDto = new OrderItemDto(1L, "JUNIT_000", 10, BigDecimal.TEN);
        return new OrderDto(151L, 1001L, "NEW", source, List.of(orderItemDto));
    }
}
