/***
<p>
    Licensed under MIT License Copyright (c) 2024-2026 Raja Kolli.
</p>
***/

package com.example.inventoryservice.config;

import com.example.common.dtos.OrderDto;
import java.util.concurrent.CountDownLatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.kafka.annotation.KafkaListener;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@TestConfiguration(proxyBeanMethods = false)
public class TestStockOrderListenerConfig {

    private static final Logger log = LoggerFactory.getLogger(TestStockOrderListenerConfig.class);

    private final CountDownLatch countDownLatch = new CountDownLatch(1);
    private final JsonMapper jsonMapper;

    public TestStockOrderListenerConfig(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public CountDownLatch getCountDownLatch() {
        return countDownLatch;
    }

    @KafkaListener(id = "stocks", topics = "stock-orders", groupId = "inventory")
    public void onOrderEvent(String orderDtoStr) throws JacksonException {
        OrderDto orderDto = jsonMapper.readValue(orderDtoStr, OrderDto.class);
        log.info("Received Order: {}", orderDto);
        countDownLatch.countDown();
    }
}
