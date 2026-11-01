/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.orderservice.services;

import com.example.common.dto.OrderDto;
import com.example.common.logging.LogSanitizer;
import com.example.common.logging.Loggable;
import com.example.orderservice.entities.Order;
import com.example.orderservice.entities.OrderStatus;
import com.example.orderservice.mapper.OrderMapper;
import com.example.orderservice.repositories.OrderRepository;
import io.micrometer.observation.annotation.Observed;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Domain service responsible for reconciling and retrying orders stuck in NEW status. */
@Service
@Loggable
@Observed(name = "orderRetryService")
public class OrderRetryService {

    private static final Logger log = LoggerFactory.getLogger(OrderRetryService.class);

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final ApplicationEventPublisher eventPublisher;

    public OrderRetryService(
            OrderRepository orderRepository,
            OrderMapper orderMapper,
            ApplicationEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.orderMapper = orderMapper;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Republishes NEW orders last modified strictly more than five minutes ago, in ascending ID
     * order, with a new random attempt ID for each. After publishing, saves the order with its last
     * modified time set to the current time. Exceptions while processing an individual order are
     * caught so remaining orders are attempted; failures fetching the orders propagate.
     */
    @Transactional
    public void retryNewOrders() {
        // fetch all orders where Status is New in Order
        List<Order> byStatusOrderByIdAsc =
                orderRepository.findByStatusAndLastModifiedDateLessThanOrderByIdAsc(
                        OrderStatus.NEW, LocalDateTime.now().minusMinutes(5));
        byStatusOrderByIdAsc.forEach(
                order -> {
                    try {
                        OrderDto persistedOrderDto =
                                this.orderMapper
                                        .toDto(order)
                                        .withAttemptId(UUID.randomUUID().toString());
                        log.info(
                                "Retrying Order :{}",
                                LogSanitizer.sanitizeForLog(String.valueOf(persistedOrderDto)));
                        eventPublisher.publishEvent(persistedOrderDto);

                        // Update lastModifiedDate to prevent immediate repolling and endless spam
                        order.setLastModifiedDate(LocalDateTime.now());
                        orderRepository.save(order);
                    } catch (Exception e) {
                        log.error("Failed to retry publishing order :{}", order.getId(), e);
                    }
                });
    }
}
