package com.example.retailstore.webapp.web.controller;

import com.example.retailstore.webapp.clients.PagedResult;
import com.example.retailstore.webapp.clients.customer.CustomerRequest;
import com.example.retailstore.webapp.clients.customer.CustomerResponse;
import com.example.retailstore.webapp.clients.customer.CustomerServiceClient;
import com.example.retailstore.webapp.clients.order.CreateOrderRequest;
import com.example.retailstore.webapp.clients.order.OrderConfirmationDTO;
import com.example.retailstore.webapp.clients.order.OrderRequestExternal;
import com.example.retailstore.webapp.clients.order.OrderResponse;
import com.example.retailstore.webapp.clients.order.OrderServiceClient;
import com.example.retailstore.webapp.exception.InvalidRequestException;
import com.example.retailstore.webapp.exception.ResourceNotFoundException;
import com.example.retailstore.webapp.services.SecurityHelper;
import jakarta.validation.Valid;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    private final OrderServiceClient orderServiceClient;
    private final CustomerServiceClient customerServiceClient;
    private final SecurityHelper securityHelper;

    OrderController(
            OrderServiceClient orderServiceClient,
            SecurityHelper securityHelper,
            CustomerServiceClient customerServiceClient) {
        this.orderServiceClient = orderServiceClient;
        this.customerServiceClient = customerServiceClient;
        this.securityHelper = securityHelper;
    }

    @GetMapping("/cart")
    String cart() {
        return "cart";
    }

    @GetMapping("/orders/{orderNumber}")
    String showOrderDetails(@PathVariable String orderNumber, Model model) {
        model.addAttribute("orderNumber", orderNumber);
        return "order_details";
    }

    @GetMapping("/api/orders/{orderNumber}")
    @ResponseBody
    OrderResponse getOrder(@PathVariable String orderNumber) {
        log.info("Fetching order details for orderNumber: {}", orderNumber);
        try {
            return orderServiceClient.getOrder(getHeaders(), orderNumber);
        } catch (Exception e) {
            log.error("Error fetching order {}: {}", orderNumber, e.getMessage());
            throw new ResourceNotFoundException("Order", "orderNumber", orderNumber);
        }
    }

    @GetMapping("/orders")
    String showOrders() {
        return "orders";
    }

    @GetMapping("/api/orders")
    @ResponseBody
    PagedResult<OrderResponse> getOrders() {
        log.info("Fetching orders");
        return orderServiceClient.getOrders(getHeaders());
    }

    private Map<String, ?> getHeaders() {
        String accessToken = securityHelper.getAccessToken();
        return Map.of("Authorization", "Bearer " + accessToken);
    }

    @PostMapping("/api/orders")
    @ResponseBody
    OrderConfirmationDTO createOrder(@Valid @RequestBody CreateOrderRequest orderRequest) {
        log.info("Creating order: {}", orderRequest);
        try {
            String email = securityHelper.getLoggedInUserEmail();
            if (email == null || email.trim().isEmpty()) {
                throw new InvalidRequestException("User email not available");
            }

            CustomerRequest customerRequest = orderRequest.customer().withEmail(email);
            CustomerResponse customerResponse = customerServiceClient.getOrCreateCustomer(customerRequest);

            OrderRequestExternal orderRequestExternal = orderRequest.withCustomerId(customerResponse.customerId());
            return orderServiceClient.createOrder(getHeaders(), orderRequestExternal);
        } catch (InvalidRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error creating order: {}", e.getMessage());
            throw new InvalidRequestException("Failed to create order: " + e.getMessage());
        }
    }
}
