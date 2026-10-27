/***
<p>
    Licensed under MIT License Copyright (c) 2024-2026 Raja Kolli.
</p>
***/

package com.example.inventoryservice.inventory.shared;

import com.example.common.dto.OrderDto;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.kafka.annotation.KafkaListener;
import tools.jackson.databind.json.JsonMapper;

@TestConfiguration(proxyBeanMethods = false)
public class TestStockOrderListenerConfig {

    private static final Logger log = LoggerFactory.getLogger(TestStockOrderListenerConfig.class);

    private volatile CountDownLatch countDownLatch = new CountDownLatch(1);
    private final List<OrderDto> receivedOrders = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final JsonMapper jsonMapper;

    public TestStockOrderListenerConfig(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public CountDownLatch getCountDownLatch() {
        return countDownLatch;
    }

    public List<OrderDto> getReceivedOrders() {
        return receivedOrders;
    }

    public void reset() {
        this.countDownLatch = new CountDownLatch(1);
        this.receivedOrders.clear();
    }

    @KafkaListener(
            id = "stocks",
            topics = "stock-orders",
            groupId = "inventory",
            properties = {
                "spring.deserializer.value.delegate.class=org.apache.kafka.common.serialization.ByteArrayDeserializer"
            })
    public void onOrderEvent(ConsumerRecord<String, byte[]> record) {
        OrderDto orderDto = jsonMapper.readValue(record.value(), OrderDto.class);
        log.info("Received Order: {}", orderDto);
        receivedOrders.add(orderDto);
        countDownLatch.countDown();
    }
}
