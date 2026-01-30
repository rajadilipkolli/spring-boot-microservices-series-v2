/***
<p>
    Licensed under MIT License Copyright (c) 2023-2025 Raja Kolli.
</p>
***/

package com.example.orderservice.mapper;

import com.example.orderservice.entities.Order;
import com.example.orderservice.entities.OrderItem;
import com.example.orderservice.model.request.OrderRequest;
import java.util.List;
import java.util.Objects;

public abstract class OrderMapperDecorator implements OrderMapper {

    @Override
    public void updateOrderFromOrderRequest(OrderRequest orderRequest, Order order) {
        order.setCustomerId(orderRequest.customerId());
        order.setDeliveryAddress(orderRequest.deliveryAddress());

        // Convert request to OrderItems
        List<OrderItem> detachedOrderItems =
                orderRequest.items().stream().map(this::orderItemRequestToOrderItem).toList();

        // Remove the existing database rows that are no
        // longer found in the incoming collection (detachedOrderItems)
        List<OrderItem> orderItemsToRemove =
                order.getItems().stream()
                        .filter(orderItem -> !detachedOrderItems.contains(orderItem))
                        .toList();
        orderItemsToRemove.forEach(order::removeOrderItem);

        // Update the existing database rows which can be found
        // in the incoming collection (detachedOrderItems)
        List<OrderItem> newOrderItems =
                detachedOrderItems.stream()
                        .filter(orderItem -> !order.getItems().contains(orderItem))
                        .toList();

        detachedOrderItems.stream()
                .filter(orderItem -> !newOrderItems.contains(orderItem))
                .forEach(
                        orderItem -> {
                            orderItem.setOrder(order);
                            orderItem.setId(getOrderItemId(order.getItems(), orderItem));
                            order.getItems().set(order.getItems().indexOf(orderItem), orderItem);
                        });

        // Add the rows found in the incoming collection,
        // which cannot be found in the current database snapshot
        newOrderItems.forEach(order::addOrderItem);
    }

    // Manual Merge instead of using `var mergedBook = orderItemRepository.save(orderItem)` which
    // calls save in middle of transaction
    private Long getOrderItemId(List<OrderItem> items, OrderItem orderItem) {
        return items.stream()
                .filter(item -> Objects.equals(item.getProductCode(), orderItem.getProductCode()))
                .map(OrderItem::getId)
                .findFirst()
                .orElse(null);
    }
}
