/* (C)2022 */
package com.example.orderservice.web.controllers;

import com.example.orderservice.dtos.OrderDto;
import com.example.orderservice.services.OrderGeneratorService;
import com.example.orderservice.services.OrderService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    private final OrderGeneratorService orderGeneratorService;

    @GetMapping
    public List<OrderDto> getAllOrders() {
        return orderService.findAllOrders();
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
    @ResponseStatus(HttpStatus.CREATED)
    public OrderDto createOrder(@RequestBody @Validated OrderDto orderDto) {
        return orderService.saveOrder(orderDto);
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
    public List<OrderDto> all() {
        return orderGeneratorService.getAllOrders();
    }
}
