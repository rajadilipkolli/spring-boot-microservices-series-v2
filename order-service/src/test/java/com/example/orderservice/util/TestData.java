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
import com.example.orderservice.model.Address;
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
        OrderItem orderItem = order.getItems().getFirst();
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

        return new OrderDto(151L, 1001L, "ACCEPT", source, getOrderItemDtoList());
    }

    public static OrderDto getStockOrderDto(String status, Order testOrder) {

        return new OrderDto(
                testOrder.getId(),
                testOrder.getCustomerId(),
                status,
                "INVENTORY",
                getOrderItemDtoList());
    }

    public static OrderDto getPaymentOrderDto(String status, Order testOrder) {
        return new OrderDto(
                testOrder.getId(),
                testOrder.getCustomerId(),
                status,
                "PAYMENT",
                getOrderItemDtoList());
    }

    private static List<OrderItemDto> getOrderItemDtoList() {
        return List.of(new OrderItemDto(1L, "Product1", 10, BigDecimal.TEN));
    }
}
