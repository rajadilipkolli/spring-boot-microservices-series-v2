/***
<p>
    Licensed under MIT License Copyright (c) 2023-2026 Raja Kolli.
</p>
***/

package com.example.catalogservice.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.catalogservice.model.payload.ProductDto;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import tools.jackson.databind.json.JsonMapper;

@TestConfiguration(proxyBeanMethods = false)
public class TestKafkaListenerConfig {

    private static final Logger log = LoggerFactory.getLogger(TestKafkaListenerConfig.class);

    private final JsonMapper jsonMapper;
    private final BlockingQueue<String> messages = new LinkedBlockingQueue<>();

    public TestKafkaListenerConfig(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @KafkaListener(id = "products", topics = "productTopic", groupId = "product")
    public void onSaveProductEvent(@Payload String productDto) {
        log.info("Received Product Payload: {}", productDto);
        assertThat(productDto.startsWith("{") && productDto.endsWith("}")).isTrue();
        try {
            ProductDto parsed = jsonMapper.readValue(productDto, ProductDto.class);
            assertThat(parsed).isNotNull();
        } catch (Exception e) {
            org.assertj.core.api.Assertions.fail("Failed to parse ProductDto from string", e);
        }
        messages.offer(productDto);
    }

    public String pollPayload(long timeout, TimeUnit unit) throws InterruptedException {
        return messages.poll(timeout, unit);
    }

    public void reset() {
        messages.clear();
    }
}
