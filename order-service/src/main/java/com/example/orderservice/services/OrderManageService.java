/***
<p>
    Licensed under MIT License Copyright (c) 2022-2026 Raja Kolli.
</p>
***/

package com.example.orderservice.services;

import com.example.orderservice.config.logging.Loggable;
import com.example.orderservice.entities.OrderStatus;
import com.example.orderservice.model.dtos.OrderDto;
import com.example.orderservice.repositories.OrderRepository;
import com.example.orderservice.utils.AppConstants;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@Loggable
public class OrderManageService {

    private static final String REJECT = "REJECT";
    private static final Logger log = LoggerFactory.getLogger(OrderManageService.class);
    private final OrderRepository orderRepository;
    private final Counter ordersCompletedCounter;
    private final Counter ordersFailedCounter;

    public OrderManageService(OrderRepository orderRepository, MeterRegistry meterRegistry) {
        this.orderRepository = orderRepository;
        this.ordersCompletedCounter = meterRegistry.counter("orders_completed");
        this.ordersFailedCounter = meterRegistry.counter("orders_failed");
    }

    /**
     * Reconciles the payment and inventory outcomes and persists the resulting order status.
     *
     * @param orderPayment payment reservation outcome
     * @param orderStock inventory reservation outcome
     * @return the reconciled order outcome
     */
    public OrderDto confirm(OrderDto orderPayment, OrderDto orderStock) {
        log.info("Setting Status for order :{}", orderPayment);
        OrderDto orderDto = getOrderDto(orderPayment, orderStock);
        int rows =
                this.orderRepository.updateOrderStatusAndSourceById(
                        orderDto.orderId(),
                        OrderStatus.valueOf(orderDto.status()),
                        orderDto.source());
        log.info(
                "Updated Status as {} for orderId :{} in {} rows",
                orderDto.status(),
                orderDto.orderId(),
                rows);
        if ("CONFIRMED".equals(orderDto.status())) {
            this.ordersCompletedCounter.increment();
        } else if ("REJECTED".equals(orderDto.status())
                || AppConstants.ROLLBACK.equals(orderDto.status())) {
            this.ordersFailedCounter.increment();
        }
        return orderDto;
    }

    /**
     * Derives the final order outcome from the payment and inventory reservation statuses.
     *
     * @param orderPayment payment reservation outcome
     * @param orderStock inventory reservation outcome
     * @return the order with its final status and rejection source, when applicable
     */
    private OrderDto getOrderDto(OrderDto orderPayment, OrderDto orderStock) {
        OrderDto orderDto = orderStock;
        if (OrderStatus.ACCEPT.name().equals(orderPayment.status())
                && OrderStatus.ACCEPT.name().equals(orderStock.status())) {
            orderDto = orderDto.withStatusAndSource("CONFIRMED", null);
        } else if (REJECT.equals(orderPayment.status()) && REJECT.equals(orderStock.status())) {
            orderDto = orderDto.withStatusAndSource("REJECTED", orderStock.source());
        } else if (REJECT.equals(orderPayment.status()) || REJECT.equals(orderStock.status())) {
            String source = REJECT.equals(orderPayment.status()) ? "PAYMENT" : "INVENTORY";
            orderDto = orderDto.withStatusAndSource(AppConstants.ROLLBACK, source);
        }
        return orderDto;
    }
}
