/*** Licensed under MIT License Copyright (c) 2022-2024 Raja Kolli. ***/
package com.example.paymentservice.services.listener;

import com.example.common.dtos.OrderDto;
import com.example.paymentservice.config.logging.Loggable;
import com.example.paymentservice.exception.CustomerNotFoundException;
import com.example.paymentservice.services.PaymentOrderManageService;
import com.example.paymentservice.utils.AppConstants;
import java.util.concurrent.CountDownLatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
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
    @RetryableTopic(
            backoff = @Backoff(delay = 1000, multiplier = 2.0),
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

    @DltHandler
    public void dlt(OrderDto orderDto, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.error("Received dead-letter message : {} from topic {}", orderDto, topic);
        deadLetterLatch.countDown();
    }

    public CountDownLatch getDeadLetterLatch() {
        return this.deadLetterLatch;
    }
}
