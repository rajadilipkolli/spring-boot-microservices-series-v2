package com.example.retailstore.webapp.clients.order;

import com.example.retailstore.webapp.clients.customer.CustomerRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.io.Serializable;
import java.util.List;

public record CreateOrderRequest(
        @NotEmpty(message = "Items cannot be empty.") List<OrderItemRequest> items,
        @Valid CustomerRequest customer,
        @Valid Address deliveryAddress)
        implements Serializable {
    public OrderRequestExternal withCustomerId(Long customerId) {
        return new OrderRequestExternal(
                customerId,
                items().stream()
                        .map(orderItemRequest -> new OrderItemExternal(
                                orderItemRequest.productCode(), orderItemRequest.quantity(), orderItemRequest.price()))
                        .toList(),
                deliveryAddress());
    }
}
