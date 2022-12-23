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
        OrderDto o = new OrderDto();
        o.setOrderId(orderPayment.getOrderId());
        if (ACCEPT.equals(orderPayment.getStatus()) && ACCEPT.equals(orderStock.getStatus())) {
            o.setStatus("CONFIRMED");
        } else if (REJECT.equals(orderPayment.getStatus())
                && REJECT.equals(orderStock.getStatus())) {
            o.setStatus("REJECTED");
        } else if (REJECT.equals(orderPayment.getStatus())
                || REJECT.equals(orderStock.getStatus())) {
            String source = REJECT.equals(orderPayment.getStatus()) ? "PAYMENT" : "STOCK";
            o.setStatus("ROLLBACK");
            o.setSource(source);
        }
        this.orderRepository.updateOrderStatusAndSourceById(
                o.getOrderId(), o.getStatus(), o.getSource());
        log.info("Updated Status for orderId :{}", o.getOrderId());
        return o;
    }
}
