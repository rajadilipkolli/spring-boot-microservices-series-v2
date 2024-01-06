/***
<p>
    Licensed under MIT License Copyright (c) 2022-2023 Raja Kolli.
</p>
***/

package com.example.orderservice.services;

import com.example.common.dtos.OrderDto;
import com.example.orderservice.entities.OrderStatus;
import com.example.orderservice.repositories.OrderRepository;
import com.example.orderservice.utils.AppConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
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
        OrderDto orderDto = new OrderDto();
        // need to set all values as the same object will be sent to downstream via kafka
        orderDto.setOrderId(orderPayment.getOrderId());
        orderDto.setCustomerId(orderPayment.getCustomerId());
        if (ACCEPT.equals(orderPayment.getStatus()) && ACCEPT.equals(orderStock.getStatus())) {
            orderDto.setStatus("CONFIRMED");
        } else if (REJECT.equals(orderPayment.getStatus())
                && REJECT.equals(orderStock.getStatus())) {
            orderDto.setStatus("REJECTED");
        } else if (REJECT.equals(orderPayment.getStatus())
                || REJECT.equals(orderStock.getStatus())) {
            String source = REJECT.equals(orderPayment.getStatus()) ? "PAYMENT" : "INVENTORY";
            orderDto.setStatus(AppConstants.ROLLBACK);
            orderDto.setSource(source);
        }
        // setting from inventory as it has latest
        orderDto.setItems(orderStock.getItems());
        int rows =
                this.orderRepository.updateOrderStatusAndSourceById(
                        orderDto.getOrderId(),
                        OrderStatus.valueOf(orderDto.getStatus()),
                        orderDto.getSource());
        log.info(
                "Updated Status as {} for orderId :{} in {} rows",
                orderDto.getStatus(),
                orderDto.getOrderId(),
                rows);
        return orderDto;
    }
}
