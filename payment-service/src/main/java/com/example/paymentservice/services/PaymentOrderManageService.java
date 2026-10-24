/*** Licensed under MIT License Copyright (c) 2022-2026 Raja Kolli. ***/
package com.example.paymentservice.services;

import com.example.common.dto.OrderDto;
import com.example.common.logging.LogSanitizer;
import com.example.common.logging.Loggable;
import com.example.paymentservice.utils.AppConstants;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@Loggable
public class PaymentOrderManageService {

    private static final Logger log = LoggerFactory.getLogger(PaymentOrderManageService.class);

    private final PaymentOperationEngine operationEngine;
    private final Counter paymentsStartedCounter;
    private final Counter paymentsSuccessfulCounter;
    private final Counter paymentsFailedCounter;

    /**
     * Creates the order payment service with its operation engine and registers payment outcome
     * counters.
     */
    public PaymentOrderManageService(
            MeterRegistry meterRegistry, PaymentOperationEngine operationEngine) {
        this.operationEngine = operationEngine;
        this.paymentsStartedCounter = meterRegistry.counter("payments_started");
        this.paymentsSuccessfulCounter = meterRegistry.counter("payments_successful");
        this.paymentsFailedCounter = meterRegistry.counter("payments_failed");
    }

    /**
     * Reserves payment through the operation engine and records payment outcome metrics. Provider
     * failures can defer the result; engine persistence and serialization failures propagate.
     *
     * @param orderDto order whose payment should be reserved
     * @return the order with source PAYMENT and status ACCEPT, REJECT, or PENDING
     * @throws com.example.paymentservice.exception.CustomerNotFoundException if the customer ID is
     *     null or absent
     * @throws com.example.paymentservice.exception.OperationConflictException if a retry changes
     *     the stored reservation fingerprint
     */
    @Timed(percentiles = 1.0)
    public OrderDto reserve(OrderDto orderDto) {
        this.paymentsStartedCounter.increment();
        log.debug(
                "Reserving Order with Id :{} in payment service with payload {}",
                LogSanitizer.sanitizeForLog(String.valueOf(orderDto.orderId())),
                LogSanitizer.sanitizeForLog(String.valueOf(orderDto)));

        OrderDto result = operationEngine.processReserve(orderDto);
        if ("ACCEPT".equals(result.status())) {
            this.paymentsSuccessfulCounter.increment();
        } else if ("REJECT".equals(result.status())) {
            this.paymentsFailedCounter.increment();
        }
        return result;
    }

    /**
     * Captures a CONFIRMED order or releases a ROLLBACK order, regardless of source; other statuses
     * have no effect. Settlement uses the stored reservation amount. Provider failures defer
     * completion; engine persistence failures propagate.
     *
     * @param orderDto the order outcome
     * @throws com.example.paymentservice.exception.CustomerNotFoundException if settlement is
     *     requested with a null or absent customer ID
     * @throws com.example.paymentservice.exception.ReservationNotFoundException if settlement is
     *     requested before a completed reservation exists
     * @throws com.example.paymentservice.exception.OperationConflictException if an applicable
     *     settlement conflicts with the stored fingerprint
     */
    @Timed(percentiles = 1.0)
    public void confirm(OrderDto orderDto) {
        log.debug(
                "Confirming Order with Id :{} in payment service with payload {}",
                LogSanitizer.sanitizeForLog(String.valueOf(orderDto.orderId())),
                LogSanitizer.sanitizeForLog(String.valueOf(orderDto)));

        if ("CONFIRMED".equals(orderDto.status())) {
            operationEngine.processSettlement(orderDto, "CAPTURE");
        } else if (AppConstants.ROLLBACK.equals(orderDto.status())) {
            operationEngine.processSettlement(orderDto, "RELEASE");
        }
    }
}
