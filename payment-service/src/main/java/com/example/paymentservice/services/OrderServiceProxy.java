/*** Licensed under MIT License Copyright (c) 2026 Raja Kolli. ***/
package com.example.paymentservice.services;

import com.example.paymentservice.model.response.OrderResponse;
import com.example.paymentservice.model.response.PagedResult;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange(
        accept = MediaType.APPLICATION_JSON_VALUE,
        contentType = MediaType.APPLICATION_JSON_VALUE)
public interface OrderServiceProxy {

    /**
     * Retrieves a page of customer orders from the order service over HTTP.
     *
     * @param page zero-based page index
     * @param size requested number of orders per page
     * @param sort sort property and direction, such as {@code id,asc}
     * @return the decoded orders and pagination metadata supplied by the order service
     * @throws org.springframework.web.client.RestClientResponseException if the service returns an
     *     HTTP error
     * @throws org.springframework.web.client.ResourceAccessException if the request fails due to an
     *     I/O error
     */
    @GetExchange("/api/orders/customer/{id}")
    PagedResult<OrderResponse> getOrdersByCustomerId(
            @PathVariable("id") Long id,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            @RequestParam(name = "sort", defaultValue = "id,asc") String sort);
}
