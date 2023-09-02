/***
<p>
    Licensed under MIT License Copyright (c) 2021-2023 Raja Kolli.
</p>
***/

package com.example.orderservice.services;

import com.example.common.dtos.OrderDto;
import com.example.orderservice.config.logging.Loggable;
import com.example.orderservice.entities.Order;
import com.example.orderservice.exception.ProductNotFoundException;
import com.example.orderservice.mapper.OrderMapper;
import com.example.orderservice.model.request.OrderItemRequest;
import com.example.orderservice.model.request.OrderRequest;
import com.example.orderservice.model.response.PagedResult;
import com.example.orderservice.repositories.OrderRepository;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Loggable
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final CatalogServiceProxy catalogServiceProxy;
    private final KafkaOrderProducer kafkaOrderProducer;

    public PagedResult<OrderDto> findAllOrders(
            int pageNo, int pageSize, String sortBy, String sortDir) {
        Sort sort =
                sortDir.equalsIgnoreCase(Sort.Direction.ASC.name())
                        ? Sort.by(sortBy).ascending()
                        : Sort.by(sortBy).descending();

        // create Pageable instance
        Pageable pageable = PageRequest.of(pageNo, pageSize, sort);
        // Fetches only ParentEntities
        Page<Order> page = orderRepository.findAll(pageable);
        // Get orderIds matching pagination
        List<Long> orderIds = page.getContent().stream().map(Order::getId).toList();
        // fetching parentAlongWithChildEnties
        List<Order> ordersWithOrderItems = orderRepository.findByIdIn(orderIds);
        // Mapping Order to OrderDTO CompletableFuture
        List<CompletableFuture<OrderDto>> completableFutureList =
                ordersWithOrderItems.stream()
                        .map(
                                order ->
                                        CompletableFuture.supplyAsync(
                                                () -> this.orderMapper.toDto(order)))
                        .toList();
        // Joining all completeable future to get DTOs
        List<OrderDto> orderListDto =
                completableFutureList.stream().map(CompletableFuture::join).toList();
        return new PagedResult<>(
                orderListDto,
                page.getTotalElements(),
                page.getNumber() + 1,
                page.getTotalPages(),
                page.isFirst(),
                page.isLast(),
                page.hasNext(),
                page.hasPrevious());
    }

    public Optional<Order> findOrderById(Long id) {
        return orderRepository.findOrderById(id);
    }

    @Transactional
    public OrderDto saveOrder(OrderRequest orderRequest) {
        // Verify if items exists
        List<String> productCodes =
                orderRequest.items().stream()
                        .map(OrderItemRequest::productCode)
                        .map(String::toUpperCase)
                        .toList();
        if (productsExistsAndInStock(productCodes)) {
            Order orderEntity = this.orderMapper.orderRequestToEntity(orderRequest);
            Order savedOrder = getPersistedOrder(orderEntity);
            OrderDto persistedOrderDto = this.orderMapper.toDto(savedOrder);
            // Should send persistedOrderDto as it contains OrderId used for subsequent processing
            kafkaOrderProducer.sendOrder(persistedOrderDto);
            return persistedOrderDto;
        } else {
            throw new ProductNotFoundException(productCodes);
        }
    }

    private boolean productsExistsAndInStock(List<String> productIds) {
        return catalogServiceProxy.productsExistsByCodes(productIds);
    }

    @Transactional
    public void deleteOrderById(Long id) {
        orderRepository.deleteById(id);
    }

    @Transactional
    public OrderDto updateOrder(OrderRequest orderRequest, Order orderObj) {
        this.orderMapper.updateOrderFromOrderRequest(orderRequest, orderObj);
        Order persistedOrder = getPersistedOrder(orderObj);
        return this.orderMapper.toDto(persistedOrder);
    }

    public Order getPersistedOrder(Order orderObj) {
        return this.orderRepository.save(orderObj);
    }

    public Optional<Order> findById(Long id) {
        return orderRepository.findById(id);
    }

    public Optional<OrderDto> findOrderByIdAsDto(Long id) {
        return orderRepository.findOrderById(id).map(this.orderMapper::toDto);
    }
}
