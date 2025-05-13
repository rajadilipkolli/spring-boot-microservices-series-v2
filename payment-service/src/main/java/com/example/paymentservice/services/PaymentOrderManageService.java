/*** Licensed under MIT License Copyright (c) 2022-2024 Raja Kolli. ***/
package com.example.paymentservice.services;

import com.example.common.dtos.OrderDto;
import com.example.common.dtos.OrderItemDto;
import com.example.paymentservice.config.logging.Loggable;
import com.example.paymentservice.entities.Customer;
import com.example.paymentservice.exception.CustomerNotFoundException;
import com.example.paymentservice.repositories.CustomerRepository;
import com.example.paymentservice.utils.AppConstants;
import io.micrometer.core.annotation.Timed;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@Loggable
public class PaymentOrderManageService {

    private static final Logger log = LoggerFactory.getLogger(PaymentOrderManageService.class);

    private final CustomerRepository customerRepository;
    private final KafkaTemplate<Long, OrderDto> kafkaTemplate;

    public PaymentOrderManageService(
            CustomerRepository customerRepository, KafkaTemplate<Long, OrderDto> kafkaTemplate) {
        this.customerRepository = customerRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Timed(percentiles = 1.0)
    public void reserve(OrderDto orderDto) {
        log.debug(
                "Reserving Order with Id :{} in payment service with payload {}",
                orderDto.getOrderId(),
                orderDto);
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
        log.info("Saving customer: {} after reserving", customer);
        customerRepository.save(customer);
        kafkaTemplate.send(AppConstants.PAYMENT_ORDERS_TOPIC, orderDto.getOrderId(), orderDto);
        log.info(
                "Sent Reserved Order: {} to topic :{}",
                orderDto,
                AppConstants.PAYMENT_ORDERS_TOPIC);
    }

    @Timed(percentiles = 1.0)
    public void confirm(OrderDto orderDto) {
        log.debug(
                "Confirming Order with Id :{} in payment service with payload {}",
                orderDto.getOrderId(),
                orderDto);
        Customer customer =
                customerRepository
                        .findById(orderDto.getCustomerId())
                        .orElseThrow(() -> new CustomerNotFoundException(orderDto.getCustomerId()));
        log.info("Found Customer: {}", customer);
        var orderPrice =
                orderDto.getItems().stream()
                        .map(OrderItemDto::getPrice)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .intValue();
        if (orderDto.getStatus().equals("CONFIRMED")) {
            customer.setAmountReserved(customer.getAmountReserved() - orderPrice);
        } else if (orderDto.getStatus().equals(AppConstants.ROLLBACK)
                && !AppConstants.SOURCE.equals(orderDto.getSource())) {
            customer.setAmountReserved(customer.getAmountReserved() - orderPrice);
            customer.setAmountAvailable(customer.getAmountAvailable() + orderPrice);
        }
        log.info("Saving customer :{} After Confirmation", customer);
        Customer saved = customerRepository.save(customer);
        log.debug("Saved customer :{}", saved);
    }
}
