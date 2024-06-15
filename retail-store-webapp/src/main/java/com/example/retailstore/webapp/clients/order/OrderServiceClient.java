package com.example.retailstore.webapp.clients.order;

import com.example.retailstore.webapp.clients.PagedResult;
import java.util.Map;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.GetExchange;

public interface OrderServiceClient {

    @GetExchange("/api/orders")
    PagedResult<OrderResponse> getOrders(@RequestHeader Map<String, ?> headers);

    @GetExchange("/api/orders/{id}")
    OrderResponse getOrder(@RequestHeader Map<String, ?> headers, @PathVariable String id);
}
