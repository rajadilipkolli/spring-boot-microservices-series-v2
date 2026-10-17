/*** Licensed under MIT License Copyright (c) 2022-2026 Raja Kolli. ***/
package com.example.paymentservice.services.listener;

import com.example.paymentservice.config.logging.Loggable;
import com.example.paymentservice.exception.CustomerNotFoundException;
import com.example.paymentservice.exception.OperationConflictException;
import com.example.paymentservice.model.payload.OrderDto;
import com.example.paymentservice.services.PaymentOrderManageService;
import com.example.paymentservice.utils.AppConstants;
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

    private final CountDownLatch deadLetterLatch = new CountDownLatch(1);

    public KafkaListenerConfig(
            PaymentOrderManageService paymentOrderManageService, JsonMapper jsonMapper) {
        this.paymentOrderManageService = paymentOrderManageService;
        this.jsonMapper = jsonMapper;
    }

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
     * Dead Letter Topic (DLT) handler for processing messages that have failed processing after all
     * retries.
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
