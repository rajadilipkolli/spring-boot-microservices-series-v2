/*** Licensed under MIT License Copyright (c) 2022-2023 Raja Kolli. ***/
package com.example.paymentservice.services;

import com.example.common.dtos.OrderDto;
import com.example.common.dtos.OrderItemDto;
import com.example.paymentservice.entities.Customer;
import com.example.paymentservice.exception.CustomerNotFoundException;
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

    private final CustomerRepository customerRepository;
    private final KafkaTemplate<Long, OrderDto> kafkaTemplate;

    public void reserve(OrderDto orderDto) {
        log.debug("Reserving Order in payment Service {}", orderDto);
        Customer customer =
                customerRepository
                        .findById(orderDto.getCustomerId())
                        .orElseThrow(() -> new CustomerNotFoundException(orderDto.getCustomerId()));
        log.info("Found Customer: {}", customer.getId());
        var totalOrderPrice =
                orderDto.getItems().stream()
                        .map(OrderItemDto::getPrice)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .intValue();
        if (totalOrderPrice < customer.getAmountAvailable()) {
            orderDto.setStatus("ACCEPT");
            customer.setAmountReserved(customer.getAmountReserved() + totalOrderPrice);
            customer.setAmountAvailable(customer.getAmountAvailable() - totalOrderPrice);
        } else {
            orderDto.setStatus("REJECT");
        }
        orderDto.setSource(AppConstants.SOURCE);
        log.info("Saving customer after reserving : {}", customer.getId());
        customerRepository.save(customer);
        kafkaTemplate.send(AppConstants.PAYMENT_ORDERS_TOPIC, orderDto.getOrderId(), orderDto);
        log.info(
                "Sent Reserved Order: {} to topic :{}",
                orderDto,
                AppConstants.PAYMENT_ORDERS_TOPIC);
    }

    public void confirm(OrderDto orderDto) {
        log.debug("Confirming Order in payment service {}", orderDto);
        Customer customer =
                customerRepository
                        .findById(orderDto.getCustomerId())
                        .orElseThrow(() -> new CustomerNotFoundException(orderDto.getCustomerId()));
        log.info("Found Customer: {}", customer.getId());
        var orderPrice =
                orderDto.getItems().stream()
                        .map(OrderItemDto::getPrice)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .intValue();
        if (orderDto.getStatus().equals("CONFIRMED")) {
            customer.setAmountReserved(customer.getAmountReserved() - orderPrice);
        } else if (orderDto.getStatus().equals("ROLLBACK")
                && !AppConstants.SOURCE.equals(orderDto.getSource())) {
            customer.setAmountReserved(customer.getAmountReserved() - orderPrice);
            customer.setAmountAvailable(customer.getAmountAvailable() + orderPrice);
        }
        log.info("Saving customer After Confirmation:{}", customer.getId());
        customerRepository.save(customer);
    }
}
