package com.example.retailstore.webapp.web.controller;

import com.example.retailstore.webapp.clients.PagedResult;
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
import com.example.retailstore.webapp.util.LogSanitizer;
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
        this.securityHelper = securityHelper;
        this.customerServiceClient = customerServiceClient;
    }

    @GetMapping("/cart")
    String cart(Model model) {
        String username = securityHelper.getUsername();
        CustomerResponse customer = customerServiceClient.getCustomerByName(username);
        model.addAttribute("customer", customer);
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
        log.info("Fetching order details for orderNumber: {}", LogSanitizer.sanitizeForLog(orderNumber));
        try {
            OrderResponse orderResponse = orderServiceClient.getOrder(getHeaders(), orderNumber);
            String email = securityHelper.getLoggedInUserEmail();
            CustomerResponse loggedInCustomer = customerServiceClient.getCustomerByEmail(email);

            if (!orderResponse.getCustomerId().equals(loggedInCustomer.customerId())) {
                log.warn(
                        "User {} attempted to fetch order {} belonging to customer {}",
                        email,
                        orderNumber,
                        orderResponse.getCustomerId());
                throw new ResourceNotFoundException("Order", "orderNumber", orderNumber);
            }

            orderResponse.updateCustomerDetails(loggedInCustomer);
            return orderResponse;
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error(
                    "Error fetching order {}: {}",
                    LogSanitizer.sanitizeForLog(orderNumber),
                    LogSanitizer.sanitizeException(e));
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
        String email = securityHelper.getLoggedInUserEmail();
        CustomerResponse loggedInCustomer = customerServiceClient.getCustomerByEmail(email);
        return orderServiceClient.getOrdersByCustomer(getHeaders(), loggedInCustomer.customerId());
    }

    private Map<String, ?> getHeaders() {
        String accessToken = securityHelper.getAccessToken();
        return Map.of("Authorization", "Bearer " + accessToken);
    }

    @PostMapping("/api/orders")
    @ResponseBody
    OrderConfirmationDTO createOrder(@Valid @RequestBody CreateOrderRequest orderRequest) {
        log.info("Creating order: {}", LogSanitizer.sanitizeForLog(String.valueOf(orderRequest)));
        try {
            String email = securityHelper.getLoggedInUserEmail();
            CustomerResponse loggedInCustomer = customerServiceClient.getCustomerByEmail(email);

            OrderRequestExternal orderRequestExternal = orderRequest.withCustomerId(loggedInCustomer.customerId());
            return orderServiceClient.createOrder(getHeaders(), orderRequestExternal);
        } catch (InvalidRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error creating order: {}", LogSanitizer.sanitizeException(e));
            throw new InvalidRequestException("Failed to create order. Please try again later.");
        }
    }
}
