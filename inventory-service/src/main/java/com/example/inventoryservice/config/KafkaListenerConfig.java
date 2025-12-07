/***
<p>
    Licensed under MIT License Copyright (c) 2022-2025 Raja Kolli.
</p>
***/

package com.example.inventoryservice.config;

import com.example.common.dtos.OrderDto;
import com.example.inventoryservice.model.payload.ProductDto;
import com.example.inventoryservice.services.InventoryOrderManageService;
import com.example.inventoryservice.services.ProductManageService;
import com.example.inventoryservice.utils.AppConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.BackOff;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@EnableKafka
@Configuration(proxyBeanMethods = false)
class KafkaListenerConfig {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private final InventoryOrderManageService orderManageService;
    private final ProductManageService productManageService;
    private final ObjectMapper objectMapper;

    KafkaListenerConfig(
            InventoryOrderManageService orderManageService,
            ProductManageService productManageService,
            ObjectMapper objectMapper) {
        this.orderManageService = orderManageService;
        this.productManageService = productManageService;
        this.objectMapper = objectMapper;
    }

    // retries if processing of event fails
    @RetryableTopic(
            backOff = @BackOff(delay = 1000, multiplier = 2.0),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE)
    @KafkaListener(id = "orders", topics = AppConstants.ORDERS_TOPIC, groupId = "stock")
    public void onEvent(OrderDto orderDto) {
        log.info("Received Order: {}", orderDto);
        if ("NEW".equals(orderDto.status())) {
            orderManageService.reserve(orderDto);
        } else {
            orderManageService.confirm(orderDto);
        }
    }

    @KafkaListener(id = "products", topics = AppConstants.PRODUCT_TOPIC, groupId = "product")
    public void onSaveProductEvent(@Payload String productDto) throws JacksonException {
        log.info("Received Product: {}", productDto);
        productManageService.manage(objectMapper.readValue(productDto, ProductDto.class));
    }

    @DltHandler
    public void dlt(OrderDto orderDto, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.error("Received dead-letter message : {} from topic {}", orderDto, topic);
    }
}
