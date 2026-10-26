package com.example.retailstore.webapp.clients.order;

import com.example.common.dto.PagedResult;
import jakarta.validation.Valid;
import org.springframework.security.oauth2.client.annotation.ClientRegistrationId;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange("/order-service")
@ClientRegistrationId("retailstore-webapp")
public interface OrderServiceClient {

    @GetExchange("/api/orders")
    PagedResult<OrderResponse> getOrders();

    @GetExchange("/api/orders/{id}")
    OrderResponse getOrder(@PathVariable String id);

    @GetExchange("/api/orders/customer/{customerId}")
    PagedResult<OrderResponse> getOrdersByCustomer(@PathVariable("customerId") Long customerId);

    @PostExchange("/api/orders")
    OrderConfirmationDTO createOrder(@Valid @RequestBody OrderRequestExternal orderRequestExternal);
}
