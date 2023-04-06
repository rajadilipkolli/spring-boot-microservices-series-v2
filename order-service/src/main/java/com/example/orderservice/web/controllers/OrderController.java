/* Licensed under Apache-2.0 2021-2023 */
package com.example.orderservice.web.controllers;

import com.example.common.dtos.OrderDto;
import com.example.orderservice.model.response.PagedResult;
import com.example.orderservice.services.OrderGeneratorService;
import com.example.orderservice.services.OrderService;
import com.example.orderservice.utils.AppConstants;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

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

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Validated
public class OrderController {

    private final OrderService orderService;

    private final OrderGeneratorService orderGeneratorService;

    @GetMapping
    public PagedResult<OrderDto> getAllOrders(
            @RequestParam(
                            value = "pageNo",
                            defaultValue = AppConstants.DEFAULT_PAGE_NUMBER,
                            required = false)
                    int pageNo,
            @RequestParam(
                            value = "pageSize",
                            defaultValue = AppConstants.DEFAULT_PAGE_SIZE,
                            required = false)
                    int pageSize,
            @RequestParam(
                            value = "sortBy",
                            defaultValue = AppConstants.DEFAULT_SORT_BY,
                            required = false)
                    String sortBy,
            @RequestParam(
                            value = "sortDir",
                            defaultValue = AppConstants.DEFAULT_SORT_DIRECTION,
                            required = false)
                    String sortDir) {
        return orderService.findAllOrders(pageNo, pageSize, sortBy, sortDir);
    }

    @GetMapping("/{id}")
    // @Retry(name = "order-api", fallbackMethod = "hardcodedResponse")
    @CircuitBreaker(name = "default", fallbackMethod = "hardcodedResponse")
    // @RateLimiter(name="default")
    // @Bulkhead(name = "order-api")
    public ResponseEntity<OrderDto> getOrderById(@PathVariable Long id) {
        return orderService
                .findOrderById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    public ResponseEntity<String> hardcodedResponse(Long id, Exception ex) {
        return ResponseEntity.ok("fallback-response for id : " + id);
    }

    @PostMapping
    public ResponseEntity<OrderDto> createOrder(@RequestBody @Valid OrderDto orderDto) {
        OrderDto response = orderService.saveOrder(orderDto);
        return ResponseEntity.created(URI.create("/api/orders/" + response.getOrderId()))
                .body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<OrderDto> updateOrder(
            @PathVariable Long id, @RequestBody OrderDto orderDto) {
        return orderService
                .findOrderById(id)
                .map(
                        orderObj -> {
                            orderDto.setOrderId(id);
                            return ResponseEntity.ok(orderService.saveOrder(orderDto));
                        })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<OrderDto> deleteOrder(@PathVariable Long id) {
        return orderService
                .findOrderById(id)
                .map(
                        order -> {
                            orderService.deleteOrderById(id);
                            return ResponseEntity.ok(order);
                        })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/generate")
    public boolean create() {
        orderGeneratorService.generate();
        return true;
    }

    @GetMapping("/all")
    public List<OrderDto> all(
            @RequestParam(
                            value = "pageNo",
                            defaultValue = AppConstants.DEFAULT_PAGE_NUMBER,
                            required = false)
                    int pageNo,
            @RequestParam(
                            value = "pageSize",
                            defaultValue = AppConstants.DEFAULT_PAGE_SIZE,
                            required = false)
                    int pageSize) {
        return orderGeneratorService.getAllOrders(pageNo, pageSize);
    }
}
