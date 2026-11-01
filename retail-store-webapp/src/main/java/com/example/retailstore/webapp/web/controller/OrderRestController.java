package com.example.retailstore.webapp.web.controller;

import com.example.common.dto.PagedResult;
import com.example.common.logging.LogSanitizer;
import com.example.retailstore.webapp.clients.order.CreateOrderRequest;
import com.example.retailstore.webapp.clients.order.OrderConfirmationDTO;
import com.example.retailstore.webapp.clients.order.OrderResponse;
import com.example.retailstore.webapp.services.OrderService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class OrderRestController {

    private static final Logger log = LoggerFactory.getLogger(OrderRestController.class);

    private final OrderService orderService;

    OrderRestController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * Retrieves an order only if it belongs to the customer identified by the current user's
     * email, and adds that customer's details to the returned order.
     *
     * @param orderNumber value forwarded as the downstream order ID
     * @return the customer's order with customer details populated
     */
    @GetMapping("/api/orders/{orderNumber}")
    OrderResponse getOrder(@PathVariable String orderNumber) {
        log.info("Fetching order details for orderNumber: {}", LogSanitizer.sanitizeForLog(orderNumber));
        return orderService.getOrder(orderNumber);
    }

    /**
     * Retrieves orders for the customer identified by the current user's email using downstream
     * default pagination. Customer lookup and order retrieval failures propagate to the caller.
     *
     * @return the customer's order page returned by the order service
     */
    @GetMapping("/api/orders")
    PagedResult<OrderResponse> getOrders() {
        log.info("Fetching orders");
        return orderService.getOrders();
    }

    /**
     * Creates an order using the customer ID resolved from the current user's email.
     *
     * @param orderRequest items and delivery address to submit; customer details are not forwarded
     * @return the downstream order confirmation
     */
    @PostMapping("/api/orders")
    OrderConfirmationDTO createOrder(@Valid @RequestBody CreateOrderRequest orderRequest) {
        log.info("Creating order: {}", LogSanitizer.sanitizeForLog(String.valueOf(orderRequest)));
        return orderService.createOrder(orderRequest);
    }
}
