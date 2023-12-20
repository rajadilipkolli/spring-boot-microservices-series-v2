/***
<p>
    Licensed under MIT License Copyright (c) 2021-2023 Raja Kolli.
</p>
***/

package com.example.orderservice.web.controllers;

import com.example.common.dtos.OrderDto;
import com.example.orderservice.exception.ProductNotFoundException;
import com.example.orderservice.model.request.OrderRequest;
import com.example.orderservice.model.response.OrderResponse;
import com.example.orderservice.model.response.PagedResult;
import com.example.orderservice.services.OrderGeneratorService;
import com.example.orderservice.services.OrderKafkaStreamService;
import com.example.orderservice.services.OrderService;
import com.example.orderservice.utils.AppConstants;
import com.example.orderservice.web.api.OrderApi;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Validated
@Slf4j
public class OrderController implements OrderApi {

    private final OrderService orderService;
    private final OrderGeneratorService orderGeneratorService;
    private final OrderKafkaStreamService orderKafkaStreamService;

    @GetMapping
    public PagedResult<OrderResponse> getAllOrders(
            @RequestParam(defaultValue = AppConstants.DEFAULT_PAGE_NUMBER, required = false)
                    int pageNo,
            @RequestParam(defaultValue = AppConstants.DEFAULT_PAGE_SIZE, required = false)
                    int pageSize,
            @RequestParam(defaultValue = AppConstants.DEFAULT_SORT_BY, required = false)
                    String sortBy,
            @RequestParam(defaultValue = AppConstants.DEFAULT_SORT_DIRECTION, required = false)
                    String sortDir) {
        return orderService.findAllOrders(pageNo, pageSize, sortBy, sortDir);
    }

    @GetMapping("/{id}")
    // @Retry(name = "order-api", fallbackMethod = "hardcodedResponse")
    @CircuitBreaker(name = "default", fallbackMethod = "hardcodedResponse")
    @RateLimiter(name = "default")
    @Bulkhead(name = "order-api")
    public ResponseEntity<OrderResponse> getOrderById(@PathVariable Long id) {
        return orderService
                .findOrderByIdAsResponse(id)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    public ResponseEntity<String> hardcodedResponse(Long id, Exception ex) {
        if (ex instanceof ProductNotFoundException productNotFoundException) {
            throw productNotFoundException;
        }
        log.error("Exception occurred ", ex);
        return ResponseEntity.ok("fallback-response for id : " + id);
    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @RequestBody @Valid OrderRequest orderRequest) {
        OrderResponse orderResponse = orderService.saveOrder(orderRequest);
        return ResponseEntity.created(URI.create("/api/orders/" + orderResponse.orderId()))
                .body(orderResponse);
    }

    @PutMapping("/{id}")
    public ResponseEntity<OrderResponse> updateOrder(
            @PathVariable Long id, @RequestBody @Valid OrderRequest orderRequest) {
        return orderService
                .findOrderById(id)
                .map(
                        orderObj ->
                                ResponseEntity.ok(orderService.updateOrder(orderRequest, orderObj)))
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Object> deleteOrder(@PathVariable Long id) {
        return orderService
                .findById(id)
                .map(
                        order -> {
                            orderService.deleteOrderById(id);
                            return ResponseEntity.accepted().build();
                        })
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    @PostMapping("/generate")
    public boolean create() {
        orderGeneratorService.generate();
        return true;
    }

    @GetMapping("/all")
    @Override
    public List<OrderDto> all(
            @RequestParam(defaultValue = AppConstants.DEFAULT_PAGE_NUMBER, required = false)
                    int pageNo,
            @RequestParam(defaultValue = AppConstants.DEFAULT_PAGE_SIZE, required = false)
                    int pageSize) {
        return orderKafkaStreamService.getAllOrders(pageNo, pageSize);
    }

    @GetMapping("/customer/{id}")
    public ResponseEntity<PagedResult<OrderResponse>> ordersByCustomerId(
            @PathVariable Long id, Pageable pageable) {
        return ResponseEntity.ok(orderService.getOrdersByCustomerId(id, pageable));
    }
}
