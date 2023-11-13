/***
<p>
    Licensed under MIT License Copyright (c) 2023 Raja Kolli.
</p>
***/

package com.example.catalogservice.config;

import com.example.common.dtos.ProductDto;
import jakarta.validation.Valid;
import java.util.concurrent.CountDownLatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;

@TestConfiguration(proxyBeanMethods = false)
public class TestKafkaListenerConfig {

    private static final Logger log = LoggerFactory.getLogger(TestKafkaListenerConfig.class);

    private final CountDownLatch latch = new CountDownLatch(10);

    @KafkaListener(id = "products", topics = "productTopic", groupId = "product")
    public void onSaveProductEvent(@Payload @Valid ProductDto productDto) {
        log.info("Received Product: {}", productDto);
        latch.countDown();
    }

    public CountDownLatch getLatch() {
        return latch;
    }
}
