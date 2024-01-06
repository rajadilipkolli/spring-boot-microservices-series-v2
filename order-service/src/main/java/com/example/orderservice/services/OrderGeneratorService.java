/***
<p>
    Licensed under MIT License Copyright (c) 2022-2023 Raja Kolli.
</p>
***/

package com.example.orderservice.services;

import com.example.orderservice.model.request.OrderItemRequest;
import com.example.orderservice.model.request.OrderRequest;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.List;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class OrderGeneratorService {

    private static final SecureRandom RAND = new SecureRandom();

    private final OrderService orderService;

    public OrderGeneratorService(OrderService orderService) {
        this.orderService = orderService;
    }

    @Async
    public void generate() {
        for (int i = 0; i < 10_000; i++) {
            int x = RAND.nextInt(5) + 1;
            OrderItemRequest orderItem =
                    new OrderItemRequest(
                            "Product" + RAND.nextInt(100) + 1, x, new BigDecimal(100 * x));
            int y = RAND.nextInt(5) + 1;
            OrderItemRequest orderItem1 =
                    new OrderItemRequest(
                            "Product" + RAND.nextInt(100) + 1, y, new BigDecimal(100 * y));
            OrderRequest o =
                    new OrderRequest(RAND.nextLong(100) + 1, List.of(orderItem, orderItem1));
            orderService.saveOrder(o);
        }
    }
}
