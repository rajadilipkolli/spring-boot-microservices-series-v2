/***
<p>
    Licensed under MIT License Copyright (c) 2024 Raja Kolli.
</p>
***/

package com.example.inventoryservice.config;

import com.example.common.dtos.OrderDto;
import java.util.concurrent.CountDownLatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;

@TestConfiguration
public class TestStockOrderListenerConfig {

    private static final Logger log = LoggerFactory.getLogger(TestStockOrderListenerConfig.class);

    private final CountDownLatch countDownLatch = new CountDownLatch(1);

    public CountDownLatch getCountDownLatch() {
        return countDownLatch;
    }

    @KafkaListener(id = "stocks", topics = "stock-orders", groupId = "inventory")
    public void onOrderEvent(@Payload OrderDto orderDto) {
        log.info("Received Product: {}", orderDto);
        countDownLatch.countDown();
    }
}
