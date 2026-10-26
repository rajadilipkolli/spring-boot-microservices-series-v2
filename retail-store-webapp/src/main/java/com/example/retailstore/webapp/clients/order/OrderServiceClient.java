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

    /**
     * Retrieves orders using the order service's default pagination and the
     * {@code retailstore-webapp} OAuth2 registration. Authorization and downstream request failures
     * propagate to the caller.
     *
     * @return the order page returned by the order service
     */
    @GetExchange("/api/orders")
    PagedResult<OrderResponse> getOrders();

    /**
     * Retrieves an order using the {@code retailstore-webapp} OAuth2 registration. Authorization
     * and downstream request failures, including a missing order, propagate to the caller.
     *
     * @param id order ID as a string
     * @return the order returned by the order service
     */
    @GetExchange("/api/orders/{id}")
    OrderResponse getOrder(@PathVariable String id);

    /**
     * Retrieves a customer's orders using the order service's default pagination and the
     * {@code retailstore-webapp} OAuth2 registration. Authorization and downstream request failures
     * propagate to the caller.
     *
     * @param customerId customer whose orders to retrieve
     * @return the customer's order page returned by the order service
     */
    @GetExchange("/api/orders/customer/{customerId}")
    PagedResult<OrderResponse> getOrdersByCustomer(@PathVariable("customerId") Long customerId);

    /**
     * Submits an order using the {@code retailstore-webapp} OAuth2 registration. Authorization and
     * downstream request failures propagate to the caller.
     *
     * @param orderRequestExternal customer ID, items, and delivery address to submit
     * @return the order confirmation returned by the order service
     */
    @PostExchange("/api/orders")
    OrderConfirmationDTO createOrder(@Valid @RequestBody OrderRequestExternal orderRequestExternal);
}
