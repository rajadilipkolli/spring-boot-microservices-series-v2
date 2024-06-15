package com.example.retailstore.webapp.web.controller;

import com.example.retailstore.webapp.clients.PagedResult;
import com.example.retailstore.webapp.clients.order.OrderResponse;
import com.example.retailstore.webapp.clients.order.OrderServiceClient;
import com.example.retailstore.webapp.services.SecurityHelper;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    private final OrderServiceClient orderServiceClient;
    private final SecurityHelper securityHelper;

    OrderController(OrderServiceClient orderServiceClient, SecurityHelper securityHelper) {
        this.orderServiceClient = orderServiceClient;
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
        return orderServiceClient.getOrder(getHeaders(), orderNumber);
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
}
