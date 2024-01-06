/***
<p>
    Licensed under MIT License Copyright (c) 2024 Raja Kolli.
</p>
***/

package com.example.inventoryservice.util;

import com.example.common.dtos.OrderDto;
import com.example.common.dtos.OrderItemDto;
import java.math.BigDecimal;
import java.util.List;

public class MockTestData {

    public static OrderDto getOrderDto(String source) {
        OrderDto orderDto = new OrderDto();
        orderDto.setOrderId(151L);
        orderDto.setCustomerId(1001L);
        orderDto.setStatus("NEW");
        orderDto.setSource(source);
        OrderItemDto orderItemDto = new OrderItemDto();
        orderItemDto.setItemId(1L);
        orderItemDto.setProductId("JUNIT_000");
        orderItemDto.setProductPrice(BigDecimal.TEN);
        orderItemDto.setQuantity(10);
        orderDto.setItems(List.of(orderItemDto));
        return orderDto;
    }
}
