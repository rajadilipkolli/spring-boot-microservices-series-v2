/*** Licensed under MIT License Copyright (c) 2026 Raja Kolli. ***/
package com.example.paymentservice.services;

import com.example.common.dto.OrderDto;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

/** Domain service for calculating order pricing. */
@Component
public class OrderPricingCalculator {

    /**
     * Sums line totals individually rounded to two decimal places using HALF_UP.
     *
     * @param orderDto the order containing items to total
     * @return the total with scale two, or 0.00 for an empty item list
     * @throws NullPointerException if the order, items, an item, its price, or its quantity is null
     */
    public BigDecimal calculateTotalOrderPrice(OrderDto orderDto) {
        return orderDto.items().stream()
                .map(OrderDto.OrderItemDto::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }
}
