/*** Licensed under MIT License Copyright (c) 2022-2025 Raja Kolli. ***/
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
import java.util.Optional;
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
    public OrderDto reserve(OrderDto orderDto) {
        log.debug(
                "Reserving Order with Id :{} in payment service with payload {}",
                orderDto.orderId(),
                orderDto);
        Optional<Customer> optionalCustomer = customerRepository.findById(orderDto.customerId());
        if (optionalCustomer.isPresent()) {
            Customer customer = optionalCustomer.get();
            log.info("Found Customer: {}", customer.getId());
            var totalOrderPrice =
                    orderDto.items().stream()
                            .map(OrderItemDto::getPrice)
                            .reduce(BigDecimal.ZERO, BigDecimal::add)
                            .doubleValue();

            if (totalOrderPrice <= customer.getAmountAvailable()) {
                orderDto = orderDto.withStatus("ACCEPT");
                customer.setAmountReserved(customer.getAmountReserved() + totalOrderPrice);
                customer.setAmountAvailable(customer.getAmountAvailable() - totalOrderPrice);
            } else {
                orderDto = orderDto.withStatus("REJECT");
            }
            log.info("Saving customer: {} after reserving", customer);
            customerRepository.save(customer);
            orderDto = orderDto.withSource(AppConstants.SOURCE);
            kafkaTemplate.send(AppConstants.PAYMENT_ORDERS_TOPIC, orderDto.orderId(), orderDto);
            log.info(
                    "Sent Reserved Order: {} to topic :{}",
                    orderDto,
                    AppConstants.PAYMENT_ORDERS_TOPIC);
        } else {
            log.error("Customer not found for id: {}", orderDto.customerId());
            throw new CustomerNotFoundException(orderDto.customerId());
        }
        return orderDto;
    }

    @Timed(percentiles = 1.0)
    public void confirm(OrderDto orderDto) {
        log.debug(
                "Confirming Order with Id :{} in payment service with payload {}",
                orderDto.orderId(),
                orderDto);
        Customer customer =
                customerRepository
                        .findById(orderDto.customerId())
                        .orElseThrow(() -> new CustomerNotFoundException(orderDto.customerId()));
        log.info("Found Customer: {}", customer);
        var orderPrice =
                orderDto.items().stream()
                        .map(OrderItemDto::getPrice)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .intValue();
        if (orderDto.status().equals("CONFIRMED")) {
            customer.setAmountReserved(customer.getAmountReserved() - orderPrice);
        } else if (orderDto.status().equals(AppConstants.ROLLBACK)
                && !AppConstants.SOURCE.equals(orderDto.source())) {
            customer.setAmountReserved(customer.getAmountReserved() - orderPrice);
            customer.setAmountAvailable(customer.getAmountAvailable() + orderPrice);
        }
        log.info("Saving customer :{} After Confirmation", customer);
        Customer saved = customerRepository.save(customer);
        log.debug("Saved customer :{}", saved);
    }
}
