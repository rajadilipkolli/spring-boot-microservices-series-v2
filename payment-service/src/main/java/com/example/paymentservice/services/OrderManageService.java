package com.example.paymentservice.services;

import com.example.paymentservice.entities.Customer;
import com.example.paymentservice.entities.Order;
import com.example.paymentservice.entities.OrderItem;
import com.example.paymentservice.repositories.CustomerRepository;
import com.example.paymentservice.utils.AppConstants;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderManageService {

    private final CustomerRepository repository;
    private final KafkaTemplate<Long, Order> template;

    public void reserve(Order order) {
        Customer customer = repository.findById(order.getCustomerId()).orElseThrow();
        log.info("Found: {}", customer);
        var orderPrice =
                order.getItems().stream()
                        .map(OrderItem::getProductPrice)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .intValue();
        if (orderPrice < customer.getAmountAvailable()) {
            order.setStatus("ACCEPT");
            customer.setAmountReserved(customer.getAmountReserved() + orderPrice);
            customer.setAmountAvailable(customer.getAmountAvailable() - orderPrice);
        } else {
            order.setStatus("REJECT");
        }
        order.setSource(AppConstants.SOURCE);
        repository.save(customer);
        template.send("payment-orders", order.getId(), order);
        log.info("Sent: {}", order);
    }

    public void confirm(Order order) {
        Customer customer = repository.findById(order.getCustomerId()).orElseThrow();
        log.info("Found: {}", customer);
        var orderPrice =
                order.getItems().stream()
                        .map(OrderItem::getProductPrice)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .intValue();
        if (order.getStatus().equals("CONFIRMED")) {
            customer.setAmountReserved(customer.getAmountReserved() - orderPrice);
            repository.save(customer);
        } else if (order.getStatus().equals("ROLLBACK")
                && !order.getSource().equals(AppConstants.SOURCE)) {
            customer.setAmountReserved(customer.getAmountReserved() - orderPrice);
            customer.setAmountAvailable(customer.getAmountAvailable() + orderPrice);
            repository.save(customer);
        }
    }
}
