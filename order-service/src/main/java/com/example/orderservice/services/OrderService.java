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
import com.example.orderservice.model.response.OrderResponse;
import com.example.orderservice.model.response.PagedResult;
import com.example.orderservice.repositories.OrderRepository;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
@Loggable
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final CatalogServiceProxy catalogServiceProxy;
    private final KafkaOrderProducer kafkaOrderProducer;

    @Transactional(readOnly = true)
    public PagedResult<OrderResponse> findAllOrders(
            int pageNo, int pageSize, String sortBy, String sortDir) {
        Sort sort =
                sortDir.equalsIgnoreCase(Sort.Direction.ASC.name())
                        ? Sort.by(sortBy).ascending()
                        : Sort.by(sortBy).descending();

        // create Pageable instance
        Pageable pageable = PageRequest.of(pageNo, pageSize, sort);
        // Error:: JpaSystem firstResult/maxResults specified with collection fetch. In memory
        // pagination was about to be applied. Failing because 'Fail on pagination over collection
        // fetch' is enabled.
        // To fix above error Fetches only ParentEntities ids and then using keys fetch Data.
        Page<Long> page = orderRepository.findAllOrders(pageable);
        return getOrderResponsePagedResult(page);
    }

    @Transactional(readOnly = true)
    public Optional<Order> findOrderById(Long id) {
        return orderRepository.findOrderById(id);
    }

    public OrderResponse saveOrder(OrderRequest orderRequest) {
        // Verify if items exists
        List<String> productCodes =
                orderRequest.items().stream()
                        .map(OrderItemRequest::productCode)
                        .map(String::toUpperCase)
                        .toList();
        if (productsExistsAndInStock(productCodes)) {
            log.debug("ProductCodes :{} exists in db, hence proceeding", productCodes);
            Order orderEntity = this.orderMapper.orderRequestToEntity(orderRequest);
            Order savedOrder = getPersistedOrder(orderEntity);
            OrderDto persistedOrderDto = this.orderMapper.toDto(savedOrder);
            // Should send persistedOrderDto as it contains OrderId used for subsequent processing
            kafkaOrderProducer.sendOrder(persistedOrderDto);
            return this.orderMapper.toResponse(savedOrder);
        } else {
            log.debug("one or more of product codes :{} does not exists in db", productCodes);
            throw new ProductNotFoundException(productCodes);
        }
    }

    private boolean productsExistsAndInStock(List<String> productIds) {
        return catalogServiceProxy.productsExistsByCodes(productIds);
    }

    public void deleteOrderById(Long id) {
        orderRepository.deleteById(id);
    }

    public OrderResponse updateOrder(OrderRequest orderRequest, Order orderObj) {
        this.orderMapper.updateOrderFromOrderRequest(orderRequest, orderObj);
        Order persistedOrder = getPersistedOrder(orderObj);
        return this.orderMapper.toResponse(persistedOrder);
    }

    public Order getPersistedOrder(Order orderObj) {
        return this.orderRepository.save(orderObj);
    }

    @Transactional(readOnly = true)
    public Optional<Order> findById(Long id) {
        return orderRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<OrderResponse> findOrderByIdAsResponse(Long id) {
        return orderRepository.findOrderById(id).map(this.orderMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public PagedResult<OrderResponse> getOrdersByCustomerId(Long customerId, Pageable pageable) {
        // Error:: JpaSystem firstResult/maxResults specified with collection fetch. In memory
        // pagination was about to be applied. Failing because 'Fail on pagination over collection
        // fetch' is enabled.
        // To fix above error Fetches only ParentEntities ids and then using keys fetch Data.
        Page<Long> page = orderRepository.findAllOrdersByCustomerId(customerId, pageable);
        // fetching parentAlongWithChildEntries
        return getOrderResponsePagedResult(page);
    }

    private PagedResult<OrderResponse> getOrderResponsePagedResult(Page<Long> page) {
        // fetching parent along With ChildEntries
        List<Order> ordersWithOrderItems = orderRepository.findByIdIn(page.getContent());
        // Mapping Order to OrderDTO CompletableFuture
        List<CompletableFuture<OrderResponse>> completableFutureList =
                ordersWithOrderItems.stream()
                        .map(
                                order ->
                                        CompletableFuture.supplyAsync(
                                                () -> this.orderMapper.toResponse(order)))
                        .toList();
        // Joining all completable Future to get DTOs
        List<OrderResponse> orderListDto =
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
}
