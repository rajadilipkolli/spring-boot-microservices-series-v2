/*** Licensed under MIT License Copyright (c) 2026 Raja Kolli. ***/
package com.example.paymentservice.services;

import com.example.paymentservice.model.response.OrderResponse;
import com.example.paymentservice.model.response.PagedResult;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class OrderProxyService {

    private static final Logger log = LoggerFactory.getLogger(OrderProxyService.class);

    private final OrderServiceProxy orderServiceProxy;

    public OrderProxyService(OrderServiceProxy orderServiceProxy) {
        this.orderServiceProxy = orderServiceProxy;
    }

    @CircuitBreaker(name = "default", fallbackMethod = "getOrdersByCustomerIdFallback")
    public PagedResult<OrderResponse> getOrdersByCustomerId(
            Long customerId, int pageNo, int pageSize, String sortBy, String sortDir) {
        return orderServiceProxy.getOrdersByCustomerId(
                customerId, pageNo, pageSize, sortBy, sortDir);
    }

    public PagedResult<OrderResponse> getOrdersByCustomerIdFallback(
            Long customerId, int pageNo, int pageSize, String sortBy, String sortDir, Exception e) {
        log.error("Fallback triggered for getting orders for customer: {}", customerId, e);
        return new PagedResult<>(List.of(), 0, pageNo, 1, true, true, false, false);
    }
}
