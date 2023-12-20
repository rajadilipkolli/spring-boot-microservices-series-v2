/***
<p>
    Licensed under MIT License Copyright (c) 2023 Raja Kolli.
</p>
***/

package com.example.orderservice.util;

import com.example.orderservice.entities.Order;
import com.example.orderservice.entities.OrderItem;
import com.example.orderservice.entities.OrderStatus;
import com.example.orderservice.model.request.OrderItemRequest;
import com.example.orderservice.model.request.OrderRequest;
import java.math.BigDecimal;
import java.util.List;

public class TestData {

    public static Order getOrder() {
        Order order = new Order();
        order.setCustomerId(1L);
        order.setStatus(OrderStatus.NEW);
        OrderItem orderItem =
                new OrderItem()
                        .setProductCode("Product1")
                        .setQuantity(10)
                        .setProductPrice(new BigDecimal("10.1"));
        OrderItem orderItem1 =
                new OrderItem()
                        .setProductCode("Product2")
                        .setQuantity(100)
                        .setProductPrice(BigDecimal.ONE);
        order.addOrderItem(orderItem);
        order.addOrderItem(orderItem1);
        return order;
    }

    public static OrderRequest getOrderRequest(Order order) {
        OrderItem orderItem = order.getItems().get(0);
        OrderItem orderItem1 = order.getItems().get(1);
        return new OrderRequest(
                order.getCustomerId(),
                List.of(
                        new OrderItemRequest(
                                orderItem.getProductCode(),
                                orderItem.getQuantity() + 100,
                                orderItem.getProductPrice()),
                        new OrderItemRequest(
                                "product4",
                                orderItem1.getQuantity(),
                                orderItem1.getProductPrice())));
    }
}
