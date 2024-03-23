/***
<p>
    Licensed under MIT License Copyright (c) 2022-2024 Raja Kolli.
</p>
***/

package com.example.orderservice.services;

import com.example.orderservice.model.request.OrderItemRequest;
import com.example.orderservice.model.request.OrderRequest;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.List;
import java.util.stream.IntStream;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class OrderGeneratorService {

    private static final int NUM_ORDERS = 10_000;
    private static final SecureRandom RAND = new SecureRandom();

    private final OrderService orderService;

    public OrderGeneratorService(OrderService orderService) {
        this.orderService = orderService;
    }

    @Async
    public void generateOrders() {
        IntStream.rangeClosed(0, NUM_ORDERS)
                .parallel()
                .forEach(
                        value -> {
                            List<OrderItemRequest> orderItems = generateOrderItems();
                            OrderRequest orderRequest =
                                    new OrderRequest(RAND.nextLong(100), orderItems);
                            orderService.saveOrder(orderRequest);
                        });
    }

    private List<OrderItemRequest> generateOrderItems() {
        int x = RAND.nextInt(5) + 1;
        int orderItem1 = RAND.nextInt(100);
        int orderItem2 = RAND.nextInt(100);
        if (orderItem1 == orderItem2) {
            orderItem2 = orderItem2 + 1;
        }

        OrderItemRequest orderItemRequest =
                new OrderItemRequest("ProductCode" + orderItem1, x, new BigDecimal(100 * x));

        int y = RAND.nextInt(5) + 1;

        OrderItemRequest orderItemRequest2 =
                new OrderItemRequest("ProductCode" + orderItem2, y, new BigDecimal(100 * y));

        return List.of(orderItemRequest, orderItemRequest2);
    }
}
