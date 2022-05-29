package com.example.orderservice.services;

import com.example.orderservice.dtos.OrderDto;
import com.example.orderservice.repositories.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderManageService {

    private static final String ACCEPT = "ACCEPT";
    private static final String REJECT = "REJECT";

    private final OrderRepository orderRepository;

    public OrderDto confirm(OrderDto orderPayment, OrderDto orderStock) {
        OrderDto o = new OrderDto();
        o.setCustomerId(orderPayment.getCustomerId());
        o.setOrderId(orderPayment.getOrderId());
        o.setCustomerAddress(orderPayment.getCustomerAddress());
        o.setCustomerEmail(orderPayment.getCustomerEmail());
        o.setItems(orderPayment.getItems());
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
        return o;
    }
}
