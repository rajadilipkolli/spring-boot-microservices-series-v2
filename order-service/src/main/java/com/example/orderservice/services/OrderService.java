/* Licensed under Apache-2.0 2021-2023 */
package com.example.orderservice.services;

import com.example.common.dtos.OrderDto;
import com.example.common.dtos.OrderItemDto;
import com.example.orderservice.entities.Order;
import com.example.orderservice.exception.ProductNotFoundException;
import com.example.orderservice.mapper.OrderMapper;
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
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final CatalogServiceProxy catalogServiceProxy;

    private final KafkaTemplate<String, OrderDto> template;

    @Transactional(readOnly = true)
    public PagedResult<OrderDto> findAllOrders(
            int pageNo, int pageSize, String sortBy, String sortDir) {
        Sort sort =
                sortDir.equalsIgnoreCase(Sort.Direction.ASC.name())
                        ? Sort.by(sortBy).ascending()
                        : Sort.by(sortBy).descending();

        // create Pageable instance
        Pageable pageable = PageRequest.of(pageNo, pageSize, sort);
        Page<Order> page = orderRepository.findAll(pageable);
        List<CompletableFuture<OrderDto>> completableFutureList =
                page.getContent().stream()
                        .map(
                                order ->
                                        CompletableFuture.supplyAsync(
                                                () -> this.orderMapper.toDto(order)))
                        .toList();
        var orderListDto = completableFutureList.stream().map(CompletableFuture::join).toList();
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
    public OrderDto saveOrder(OrderDto orderDto) {
        // Verify if items exists
        List<String> productIds =
                orderDto.getItems().stream()
                        .map(OrderItemDto::getProductId)
                        .map(String::toUpperCase)
                        .toList();
        if (productsExistsAndInStock(productIds)) {
            Order order = this.orderMapper.toEntity(orderDto);
            OrderDto persistedOrderDto = this.orderMapper.toDto(orderRepository.save(order));
            // Should send persistedOrderDto as it contains OrderId used for subsequent processing
            this.template.send(
                    AppConstants.ORDERS_TOPIC,
                    String.valueOf(persistedOrderDto.getOrderId()),
                    persistedOrderDto);
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
        return catalogServiceProxy.productsExists(productIds);
    }

    public void deleteOrderById(Long id) {
        orderRepository.deleteById(id);
    }
}
