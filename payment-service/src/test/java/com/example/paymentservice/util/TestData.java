/*** Licensed under MIT License Copyright (c) 2024-2025 Raja Kolli. ***/
package com.example.paymentservice.util;

import com.example.common.dtos.OrderDto;
import com.example.paymentservice.entities.Customer;

public class TestData {
    public static Customer getCustomer() {
        return new Customer().setId(1L).setAmountAvailable(1000).setAmountReserved(100);
    }

    public static OrderDto withCustomerId(long nonExistentCustomerId, OrderDto orderDto) {
        return new OrderDto(
                orderDto.orderId(),
                nonExistentCustomerId,
                orderDto.status(),
                orderDto.source(),
                orderDto.items());
    }
}
