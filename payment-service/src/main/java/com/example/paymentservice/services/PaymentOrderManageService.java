/*** Licensed under MIT License Copyright (c) 2022-2026 Raja Kolli. ***/
package com.example.paymentservice.services;

import com.example.paymentservice.config.logging.Loggable;
import com.example.paymentservice.model.payload.OrderDto;
import com.example.paymentservice.utils.AppConstants;
import com.example.paymentservice.utils.LogSanitizer;
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

    public PaymentOrderManageService(
            MeterRegistry meterRegistry, PaymentOperationEngine operationEngine) {
        this.operationEngine = operationEngine;
        this.paymentsStartedCounter = meterRegistry.counter("payments_started");
        this.paymentsSuccessfulCounter = meterRegistry.counter("payments_successful");
        this.paymentsFailedCounter = meterRegistry.counter("payments_failed");
    }

    /**
     * Delegates order payment reservation to the short-transaction operation engine.
     *
     * @param orderDto order whose payment should be reserved
     * @return the order outcome
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
     * Delegates order confirmation or rollback to the short-transaction operation engine.
     *
     * @param orderDto the order outcome
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
