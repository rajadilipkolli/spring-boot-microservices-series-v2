/***
<p>
    Licensed under MIT License Copyright (c) 2022-2025 Raja Kolli.
</p>
***/

package com.example.orderservice.services;

import com.example.common.dtos.OrderDto;
import com.example.orderservice.config.logging.Loggable;
import com.example.orderservice.entities.OrderStatus;
import com.example.orderservice.repositories.OrderRepository;
import com.example.orderservice.utils.AppConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@Loggable
public class OrderManageService {

    private static final String ACCEPT = "ACCEPT";
    private static final String REJECT = "REJECT";
    private static final Logger log = LoggerFactory.getLogger(OrderManageService.class);
    private final OrderRepository orderRepository;

    public OrderManageService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

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
        return orderDto;
    }

    private OrderDto getOrderDto(OrderDto orderPayment, OrderDto orderStock) {
        OrderDto orderDto = orderStock;
        if (ACCEPT.equals(orderPayment.status()) && ACCEPT.equals(orderStock.status())) {
            orderDto = orderDto.withStatus("CONFIRMED");
        } else if (REJECT.equals(orderPayment.status()) && REJECT.equals(orderStock.status())) {
            orderDto = orderDto.withStatus("REJECTED");
        } else if (REJECT.equals(orderPayment.status()) || REJECT.equals(orderStock.status())) {
            String source = REJECT.equals(orderPayment.status()) ? "PAYMENT" : "INVENTORY";
            orderDto = orderDto.withStatus(AppConstants.ROLLBACK);
            orderDto = orderDto.withSource(source);
        }
        return orderDto;
    }
}
