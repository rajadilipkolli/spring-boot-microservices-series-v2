/***
<p>
    Licensed under MIT License Copyright (c) 2023-2024 Raja Kolli.
</p>
***/

package com.example.orderservice.util;

import com.example.common.dtos.OrderDto;
import com.example.common.dtos.OrderItemDto;
import com.example.orderservice.entities.Order;
import com.example.orderservice.entities.OrderItem;
import com.example.orderservice.entities.OrderStatus;
import com.example.orderservice.model.request.Address;
import com.example.orderservice.model.request.OrderItemRequest;
import com.example.orderservice.model.request.OrderRequest;
import java.math.BigDecimal;
import java.util.List;

public class TestData {

    public static Order getOrder() {
        Order order =
                new Order()
                        .setCustomerId(1L)
                        .setStatus(OrderStatus.NEW)
                        .setDeliveryAddress(
                                new Address(
                                        "Junit Address1",
                                        "AddressLine2",
                                        "city",
                                        "state",
                                        "zipCode",
                                        "country"));
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
                                orderItem1.getProductPrice())),
                new Address(
                        "Junit Address1", "AddressLine2", "city", "state", "zipCode", "country"));
    }

    public static OrderDto getOrderDto(String source) {
        OrderDto orderDto = new OrderDto();
        orderDto.setOrderId(151L);
        orderDto.setCustomerId(1001L);
        orderDto.setStatus("ACCEPT");
        orderDto.setSource(source);
        OrderItemDto orderItemDto = new OrderItemDto();
        orderItemDto.setItemId(1L);
        orderItemDto.setProductId("P1");
        orderItemDto.setProductPrice(BigDecimal.TEN);
        orderItemDto.setQuantity(1);
        orderDto.setItems(List.of(orderItemDto));
        return orderDto;
    }
}
