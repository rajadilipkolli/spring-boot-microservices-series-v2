/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.orderservice.config;

import com.example.common.dtos.OrderDto;
import com.example.orderservice.utils.AppConstants;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;

@TestConfiguration(proxyBeanMethods = false)
public class TestKafkaListenerConfig {

    private static final Logger log = LoggerFactory.getLogger(TestKafkaListenerConfig.class);

    private final BlockingQueue<OrderDto> messages = new LinkedBlockingQueue<>();

    @KafkaListener(
            id = "orders-test",
            topics = AppConstants.ORDERS_TOPIC,
            groupId = "orders-test-group")
    public void onOrderEvent(@Payload OrderDto orderDto) {
        log.info("Received Order Payload in test: {}", orderDto);
        messages.offer(orderDto);
    }

    public OrderDto pollPayload(long timeout, TimeUnit unit) throws InterruptedException {
        return messages.poll(timeout, unit);
    }

    public void reset() {
        messages.clear();
    }
}
