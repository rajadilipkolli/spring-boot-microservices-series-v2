/*** Licensed under MIT License Copyright (c) 2024-2026 Raja Kolli. ***/
package com.example.paymentservice.util;

import com.example.paymentservice.entities.Customer;
import com.example.paymentservice.model.payload.OrderDto;
import java.math.BigDecimal;

public class TestData {
    /**
     * Creates a customer fixture with 1000 available, 100 reserved, and initial version zero.
     *
     * @return a new customer with decimal balances
     */
    public static Customer getCustomer() {
        return new Customer()
                .setId(1L)
                .setAmountAvailable(BigDecimal.valueOf(1000))
                .setAmountReserved(BigDecimal.valueOf(100))
                .setVersion(0);
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
