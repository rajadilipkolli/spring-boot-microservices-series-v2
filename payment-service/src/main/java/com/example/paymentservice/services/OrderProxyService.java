/*** Licensed under MIT License Copyright (c) 2026 Raja Kolli. ***/
package com.example.paymentservice.services;

import com.example.paymentservice.model.response.OrderResponse;
import com.example.paymentservice.model.response.PagedResult;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.stereotype.Service;

@Service
public class OrderProxyService {

    private final OrderServiceProxy orderServiceProxy;

    /** Creates the service with the HTTP proxy used to retrieve customer orders. */
    public OrderProxyService(OrderServiceProxy orderServiceProxy) {
        this.orderServiceProxy = orderServiceProxy;
    }

    /**
     * Fetches customer orders through the {@code default} circuit breaker without a fallback.
     *
     * @param pageNo page number starting at 1, converted to a zero-based index without validation
     * @param pageSize requested number of orders per page, forwarded without validation
     * @param sortBy order property to sort by
     * @param sortDir sort direction appended to the property, such as {@code asc} or {@code desc}
     * @return the order service's page and pagination metadata unchanged
     * @throws org.springframework.web.client.RestClientResponseException if the order service
     *     returns an HTTP error
     * @throws org.springframework.web.client.ResourceAccessException if the HTTP request fails
     *     due to an I/O error
     * @throws io.github.resilience4j.circuitbreaker.CallNotPermittedException if the Spring-managed
     *     circuit breaker rejects the call
     */
    @CircuitBreaker(name = "default")
    public PagedResult<OrderResponse> getOrdersByCustomerId(
            Long customerId, int pageNo, int pageSize, String sortBy, String sortDir) {
        return orderServiceProxy.getOrdersByCustomerId(
                customerId, pageNo - 1, pageSize, sortBy + "," + sortDir);
    }
}
