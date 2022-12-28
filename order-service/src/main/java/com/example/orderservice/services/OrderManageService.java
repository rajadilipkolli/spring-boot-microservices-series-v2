/* Licensed under Apache-2.0 2022 */
package com.example.orderservice.services;

import com.example.common.dtos.OrderDto;
import com.example.orderservice.repositories.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderManageService {

    private static final String ACCEPT = "ACCEPT";
    private static final String REJECT = "REJECT";

    private final OrderRepository orderRepository;

    public OrderDto confirm(OrderDto orderPayment, OrderDto orderStock) {
        log.info("Setting Status for order :{}", orderPayment);
        OrderDto orderDto = new OrderDto();
        orderDto.setOrderId(orderPayment.getOrderId());
        if (ACCEPT.equals(orderPayment.getStatus()) && ACCEPT.equals(orderStock.getStatus())) {
            orderDto.setStatus("CONFIRMED");
        } else if (REJECT.equals(orderPayment.getStatus())
                && REJECT.equals(orderStock.getStatus())) {
            orderDto.setStatus("REJECTED");
        } else if (REJECT.equals(orderPayment.getStatus())
                || REJECT.equals(orderStock.getStatus())) {
            String source = REJECT.equals(orderPayment.getStatus()) ? "PAYMENT" : "STOCK";
            orderDto.setStatus("ROLLBACK");
            orderDto.setSource(source);
        }
        this.orderRepository.updateOrderStatusAndSourceById(
                orderDto.getOrderId(), orderDto.getStatus(), orderDto.getSource());
        log.info("Updated Status for orderId :{}", orderDto.getOrderId());
        return orderDto;
    }
}
