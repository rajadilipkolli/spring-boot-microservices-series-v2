/***
<p>
    Licensed under MIT License Copyright (c) 2024-2026 Raja Kolli.
</p>
***/

package com.example.inventoryservice.inventory.config;

import com.example.inventoryservice.inventory.model.payload.OrderDto;
import java.util.concurrent.CountDownLatch;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.kafka.annotation.KafkaListener;

@TestConfiguration(proxyBeanMethods = false)
public class TestStockOrderListenerConfig {

    private static final Logger log = LoggerFactory.getLogger(TestStockOrderListenerConfig.class);

    private final CountDownLatch countDownLatch = new CountDownLatch(1);

    public CountDownLatch getCountDownLatch() {
        return countDownLatch;
    }

    @KafkaListener(
            id = "stocks",
            topics = "stock-orders",
            groupId = "inventory",
            properties = {
                "spring.deserializer.value.delegate.class=org.springframework.kafka.support.serializer.JsonDeserializer",
                "spring.json.trusted.packages=com.example.inventoryservice.inventory.events"
            })
    public void onOrderEvent(ConsumerRecord<String, OrderDto> record) {
        OrderDto orderDto = record.value();
        log.info("Received Order: {}", orderDto);
        countDownLatch.countDown();
    }
}
