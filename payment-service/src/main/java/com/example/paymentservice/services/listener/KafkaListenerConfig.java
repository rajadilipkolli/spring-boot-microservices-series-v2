/*** Licensed under MIT License Copyright (c) 2022-2026 Raja Kolli. ***/
package com.example.paymentservice.services.listener;

import com.example.common.dto.OrderDto;
import com.example.common.logging.Loggable;
import com.example.paymentservice.exception.CustomerNotFoundException;
import com.example.paymentservice.exception.OperationConflictException;
import com.example.paymentservice.services.PaymentOrderManageService;
import com.example.paymentservice.utils.AppConstants;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.BackOff;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Component
@EnableKafka
@Loggable
public class KafkaListenerConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaListenerConfig.class);

    private final PaymentOrderManageService paymentOrderManageService;
    private final JsonMapper jsonMapper;
    private final Validator validator;

    private final CountDownLatch deadLetterLatch = new CountDownLatch(1);

    /**
     * Creates the listener with payment processing, JSON deserialization, and payload validation.
     */
    public KafkaListenerConfig(
            PaymentOrderManageService paymentOrderManageService,
            JsonMapper jsonMapper,
            Validator validator) {
        this.paymentOrderManageService = paymentOrderManageService;
        this.jsonMapper = jsonMapper;
        this.validator = validator;
    }

    /**
     * Deserializes and validates an order event, then reserves NEW orders or forwards other
     * statuses for settlement. Missing-customer, illegal-argument, and operation-conflict errors
     * bypass retries; other propagated errors, including a missing completed reservation, remain
     * retryable. Provider failures defer processing; persistence and reply serialization failures
     * propagate.
     *
     * @param orderDtoStr the JSON order event
     * @throws JacksonException if the event cannot be deserialized
     * @throws IllegalArgumentException if the payload violates its bean validation constraints or a
     *     NEW order's attempt ID is null or blank after trimming, or its calculated total is
     *     negative
     * @throws NullPointerException if a NEW order contains a null item, price, or quantity
     * @throws CustomerNotFoundException if the requested operation's customer ID is null or absent
     * @throws OperationConflictException if the operation conflicts with its stored fingerprint
     * @throws com.example.paymentservice.exception.ReservationNotFoundException if settlement
     *     requires a reservation that is absent or incomplete
     */
    // retries if processing of event fails
    // Exclude CustomerNotFoundException, IllegalArgumentException (for missing attemptId), and
    // OperationConflictException
    // Keep ReservationNotFoundException retryable
    @RetryableTopic(
            backOff = @BackOff(delay = 1000, multiplier = 2.0),
            exclude = {
                CustomerNotFoundException.class,
                OperationConflictException.class,
                IllegalArgumentException.class
            },
            retryTopicSuffix = "-retry-payment",
            dltTopicSuffix = "-dlt-payment",
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE)
    @KafkaListener(id = "payment-orders", topics = AppConstants.ORDERS_TOPIC, groupId = "payment")
    public void onEvent(String orderDtoStr) throws JacksonException {
        OrderDto orderDto = jsonMapper.readValue(orderDtoStr, OrderDto.class);
        Set<ConstraintViolation<OrderDto>> violations = validator.validate(orderDto);
        if (!violations.isEmpty()) {
            throw new IllegalArgumentException("Payload validation failed: " + violations);
        }
        log.info(
                "Received Order in payment service : {} from topic: {} with source :{}",
                orderDto,
                AppConstants.ORDERS_TOPIC,
                orderDto.source());
        if ("NEW".equals(orderDto.status())) {
            if (orderDto.attemptId() == null || orderDto.attemptId().trim().isEmpty()) {
                throw new IllegalArgumentException(
                        "attemptId must not be null or blank for NEW orders");
            }
            paymentOrderManageService.reserve(orderDto);
        } else {
            paymentOrderManageService.confirm(orderDto);
        }
    }

    /**
     * Handles a dead-letter message and counts down the dead-letter latch. Parsing failures are
     * caught so malformed messages still release the latch. Messages can reach the DLT after
     * retries are exhausted or when their failure is excluded from retries.
     *
     * @param orderDtoStr The failed order message that reached the DLT
     * @param topic The Kafka topic from which the dead letter message was received
     */
    @DltHandler
    public void dlt(String orderDtoStr, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        String operationKey = "unknown";
        try {
            OrderDto dto = jsonMapper.readValue(orderDtoStr, OrderDto.class);
            if (dto.orderId() != null) {
                operationKey =
                        "payment:"
                                + dto.orderId()
                                + ":"
                                + ("NEW".equals(dto.status()) ? "RESERVE" : "SETTLE");
            }
        } catch (Exception ignored) {
        }
        log.error(
                "Received dead-letter message for operation {} from topic {}: {}",
                operationKey,
                topic,
                orderDtoStr);
        deadLetterLatch.countDown();
    }

    public CountDownLatch getDeadLetterLatch() {
        return this.deadLetterLatch;
    }
}
