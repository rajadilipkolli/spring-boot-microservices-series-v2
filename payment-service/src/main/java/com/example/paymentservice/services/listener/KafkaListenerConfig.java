/*** Licensed under MIT License Copyright (c) 2022-2025 Raja Kolli. ***/
package com.example.paymentservice.services.listener;

import com.example.common.dtos.OrderDto;
import com.example.paymentservice.config.logging.Loggable;
import com.example.paymentservice.exception.CustomerNotFoundException;
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

@Component
@EnableKafka
@Loggable
public class KafkaListenerConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaListenerConfig.class);

    private final PaymentOrderManageService paymentOrderManageService;

    private final CountDownLatch deadLetterLatch = new CountDownLatch(1);

    public KafkaListenerConfig(PaymentOrderManageService paymentOrderManageService) {
        this.paymentOrderManageService = paymentOrderManageService;
    }

    // retries if processing of event fails
    // @RetryableTopic annotation configures retry behavior for failed message processing:
// - Initial retry delay of 1000ms that doubles with each attempt (multiplier=2.0)
// - Excludes CustomerNotFoundException from retries since it's a permanent failure
// - Creates retry topics with indexed suffixes (e.g. topic-retry-0, topic-retry-1)
// @KafkaListener configures the Kafka consumer:
// - Consumer group ID "payment" for load balancing across instances
// - Listens to the ORDERS_TOPIC defined in AppConstants
// - Unique listener ID "orders" to identify this consumer
@RetryableTopic(
            backOff = @BackOff(delay = 1000, multiplier = 2.0),
            exclude = {CustomerNotFoundException.class},
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE)
    @KafkaListener(id = "orders", topics = AppConstants.ORDERS_TOPIC, groupId = "payment")
    public void onEvent(OrderDto orderDto) {
        log.info(
                "Received Order in payment service : {} from topic: {} with source :{}",
                orderDto,
                AppConstants.ORDERS_TOPIC,
                orderDto.source());
        if ("NEW".equals(orderDto.status())) {
            paymentOrderManageService.reserve(orderDto);
        } else {
            paymentOrderManageService.confirm(orderDto);
        }
    }

    /**
 * Dead Letter Topic (DLT) handler for processing messages that have failed processing after all retries
 * This method is invoked when a message reaches the dead letter topic after exhausting retry attempts
 * 
 * @param orderDto The failed order message that reached the DLT
 * @param topic The Kafka topic from which the dead letter message was received
 */
@DltHandler
    public void dlt(OrderDto orderDto, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        // Log the failed message and topic at error level for monitoring/debugging
        log.error("Received dead-letter message : {} from topic {}", orderDto, topic);
        // Decrement latch to signal that a DLT message was processed
        // This is useful for testing and monitoring DLT handling
        deadLetterLatch.countDown();
    }

    public CountDownLatch getDeadLetterLatch() {
        return this.deadLetterLatch;
    }
}
