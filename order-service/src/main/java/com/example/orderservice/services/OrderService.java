/* Licensed under Apache-2.0 2021-2023 */
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
import com.example.orderservice.utils.AppConstants;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@Transactional
@RequiredArgsConstructor
@Loggable
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final CatalogServiceProxy catalogServiceProxy;

    private final KafkaTemplate<Long, OrderDto> template;

    @Transactional(readOnly = true)
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

    @Transactional(readOnly = true)
    public Optional<OrderDto> findOrderById(Long id) {
        return orderRepository.findOrderById(id).map(this.orderMapper::toDto);
    }

    @Transactional
    public OrderDto saveOrder(OrderRequest orderRequest) {
        // Verify if items exists
        List<String> productIds =
                orderRequest.items().stream()
                        .map(OrderItemRequest::productId)
                        .map(String::toUpperCase)
                        .toList();
        if (productsExistsAndInStock(productIds)) {
            Order order = this.orderMapper.orderRequestToEntity(orderRequest);
            OrderDto persistedOrderDto = this.orderMapper.toDto(orderRepository.save(order));
            // Should send persistedOrderDto as it contains OrderId used for subsequent processing
            this.template.send(
                    AppConstants.ORDERS_TOPIC, persistedOrderDto.getOrderId(), persistedOrderDto);
            log.info(
                    "Sent Order : {} from order service to topic {}",
                    persistedOrderDto,
                    AppConstants.ORDERS_TOPIC);

            return persistedOrderDto;
        } else {
            throw new ProductNotFoundException(productIds);
        }
    }

    private boolean productsExistsAndInStock(List<String> productIds) {
        return catalogServiceProxy.productsExistsByCodes(productIds);
    }

    public void deleteOrderById(Long id) {
        orderRepository.deleteById(id);
    }

    public OrderDto updateOrder(OrderRequest orderRequest, Order orderObj) {
        this.orderMapper.updateOrderFromOrderRequest(orderRequest, orderObj);
        Order persistedOrder = this.orderRepository.save(orderObj);
        return this.orderMapper.toDto(persistedOrder);
    }

    public Optional<Order> findById(Long id) {
        return orderRepository.findOrderById(id);
    }
}
