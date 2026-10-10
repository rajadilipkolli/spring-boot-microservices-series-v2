/*** Licensed under MIT License Copyright (c) 2022-2026 Raja Kolli. ***/
package com.example.paymentservice.services;

import com.example.paymentservice.config.logging.Loggable;
import com.example.paymentservice.entities.Customer;
import com.example.paymentservice.exception.CustomerNotFoundException;
import com.example.paymentservice.model.payload.OrderDto;
import com.example.paymentservice.repositories.CustomerRepository;
import com.example.paymentservice.utils.AppConstants;
import com.example.paymentservice.utils.LogSanitizer;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private final KafkaTemplate<String, OrderDto> kafkaTemplate;
    private final Counter paymentsStartedCounter;
    private final Counter paymentsSuccessfulCounter;
    private final Counter paymentsFailedCounter;

    public PaymentOrderManageService(
            MeterRegistry meterRegistry,
            CustomerRepository customerRepository,
            KafkaTemplate<String, OrderDto> kafkaTemplate) {
        this.customerRepository = customerRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.paymentsStartedCounter = meterRegistry.counter("payments_started");
        this.paymentsSuccessfulCounter = meterRegistry.counter("payments_successful");
        this.paymentsFailedCounter = meterRegistry.counter("payments_failed");
    }

    /**
     * Sums line totals after each is rounded to two decimal places using HALF_UP.
     *
     * @return the total with scale two, or 0.00 for an empty item list
     * @throws NullPointerException if the order, item list, an item, its price, or its quantity is null
     */
    private BigDecimal calculateTotalOrderPrice(OrderDto orderDto) {
        return orderDto.items().stream()
                .map(OrderDto.OrderItemDto::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Moves the sum of individually rounded line totals from available to reserved balance when
     * sufficient funds exist, including an exact balance match. Otherwise, rejects the order
     * without changing balances. Saves the customer and sends the outcome to Kafka in either case.
     * Database, optimistic locking, and synchronous send failures propagate; asynchronous delivery
     * is not awaited.
     *
     * @param orderDto order whose payment should be reserved
     * @return the order with status ACCEPT or REJECT and source PAYMENT
     * @throws CustomerNotFoundException if the order's customer does not exist
     */
    @Timed(percentiles = 1.0)
    public OrderDto reserve(OrderDto orderDto) {
        this.paymentsStartedCounter.increment();
        log.debug(
                "Reserving Order with Id :{} in payment service with payload {}",
                LogSanitizer.sanitizeForLog(String.valueOf(orderDto.orderId())),
                LogSanitizer.sanitizeForLog(String.valueOf(orderDto)));
        Optional<Customer> optionalCustomer = customerRepository.findById(orderDto.customerId());
        if (optionalCustomer.isPresent()) {
            Customer customer = optionalCustomer.get();
            log.info(
                    "Found Customer: {}",
                    LogSanitizer.sanitizeForLog(String.valueOf(customer.getId())));
            var totalOrderPrice = calculateTotalOrderPrice(orderDto);

            if (totalOrderPrice.compareTo(customer.getAmountAvailable()) <= 0) {
                orderDto = orderDto.withStatus("ACCEPT");
                this.paymentsSuccessfulCounter.increment();
                customer.setAmountReserved(customer.getAmountReserved().add(totalOrderPrice));
                customer.setAmountAvailable(
                        customer.getAmountAvailable().subtract(totalOrderPrice));
            } else {
                orderDto = orderDto.withStatus("REJECT");
                this.paymentsFailedCounter.increment();
            }
            log.info(
                    "Saving customer: {} after reserving",
                    LogSanitizer.sanitizeForLog(String.valueOf(customer)));
            customerRepository.save(customer);
            orderDto = orderDto.withSource(AppConstants.SOURCE);
            kafkaTemplate.send(
                    AppConstants.PAYMENT_ORDERS_TOPIC,
                    String.valueOf(orderDto.orderId()),
                    orderDto);
            log.info(
                    "Sent Reserved Order: {} to topic :{}",
                    LogSanitizer.sanitizeForLog(String.valueOf(orderDto)),
                    AppConstants.PAYMENT_ORDERS_TOPIC);
        } else {
            log.error("Customer not found for id: {}", orderDto.customerId());
            throw new CustomerNotFoundException(orderDto.customerId());
        }
        return orderDto;
    }

    /**
     * Deducts the sum of individually rounded line totals from reserved balance for CONFIRMED
     * orders. For ROLLBACK orders from a source other than PAYMENT, also restores that amount to
     * available balance. Other status/source combinations leave balances unchanged. Saves the
     * customer in every case; database and optimistic locking failures propagate.
     *
     * @param orderDto the order outcome and items used to calculate the amount
     * @throws CustomerNotFoundException if the order's customer does not exist
     */
    @Timed(percentiles = 1.0)
    public void confirm(OrderDto orderDto) {
        log.debug(
                "Confirming Order with Id :{} in payment service with payload {}",
                LogSanitizer.sanitizeForLog(String.valueOf(orderDto.orderId())),
                LogSanitizer.sanitizeForLog(String.valueOf(orderDto)));
        Customer customer =
                customerRepository
                        .findById(orderDto.customerId())
                        .orElseThrow(() -> new CustomerNotFoundException(orderDto.customerId()));
        log.info("Found Customer: {}", LogSanitizer.sanitizeForLog(String.valueOf(customer)));
        var orderPrice = calculateTotalOrderPrice(orderDto);
        if (orderDto.status().equals("CONFIRMED")) {
            customer.setAmountReserved(customer.getAmountReserved().subtract(orderPrice));
        } else if (orderDto.status().equals(AppConstants.ROLLBACK)
                && !AppConstants.SOURCE.equals(orderDto.source())) {
            customer.setAmountReserved(customer.getAmountReserved().subtract(orderPrice));
            customer.setAmountAvailable(customer.getAmountAvailable().add(orderPrice));
        }
        log.info(
                "Saving customer :{} After Confirmation",
                LogSanitizer.sanitizeForLog(String.valueOf(customer)));
        Customer saved = customerRepository.save(customer);
        log.debug("Saved customer :{}", LogSanitizer.sanitizeForLog(String.valueOf(saved)));
    }
}
