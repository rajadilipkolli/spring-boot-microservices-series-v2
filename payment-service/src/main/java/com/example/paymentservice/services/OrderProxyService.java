/*** Licensed under MIT License Copyright (c) 2026 Raja Kolli. ***/
package com.example.paymentservice.services;

import com.example.paymentservice.model.response.OrderResponse;
import com.example.paymentservice.model.response.PagedResult;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.stereotype.Service;

@Service
public class OrderProxyService {

    private final OrderServiceProxy orderServiceProxy;

    public OrderProxyService(OrderServiceProxy orderServiceProxy) {
        this.orderServiceProxy = orderServiceProxy;
    }

    @CircuitBreaker(name = "default")
    public PagedResult<OrderResponse> getOrdersByCustomerId(
            Long customerId, int pageNo, int pageSize, String sortBy, String sortDir) {
        return orderServiceProxy.getOrdersByCustomerId(
                customerId, pageNo - 1, pageSize, sortBy + "," + sortDir);
    }
}
