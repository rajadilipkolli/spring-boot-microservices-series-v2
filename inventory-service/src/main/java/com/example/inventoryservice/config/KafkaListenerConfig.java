/***
<p>
    Licensed under MIT License Copyright (c) 2022-2026 Raja Kolli.
</p>
***/

package com.example.inventoryservice.config;

import com.example.inventoryservice.model.payload.OrderDto;
import com.example.inventoryservice.model.payload.ProductDto;
import com.example.inventoryservice.services.InventoryOrderManageService;
import com.example.inventoryservice.services.ProductManageService;
import com.example.inventoryservice.utils.AppConstants;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.util.Set;
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
import tools.jackson.databind.json.JsonMapper;

@EnableKafka
@Configuration(proxyBeanMethods = false)
class KafkaListenerConfig {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private final InventoryOrderManageService orderManageService;
    private final ProductManageService productManageService;
    private final JsonMapper jsonMapper;
    private final Validator validator;

    KafkaListenerConfig(
            InventoryOrderManageService orderManageService,
            ProductManageService productManageService,
            JsonMapper jsonMapper,
            Validator validator) {
        this.orderManageService = orderManageService;
        this.productManageService = productManageService;
        this.jsonMapper = jsonMapper;
        this.validator = validator;
    }

    // retries if processing of event fails
    @RetryableTopic(
            backOff = @BackOff(delay = 1000, multiplier = 2.0),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE)
    @KafkaListener(id = "orders", topics = AppConstants.ORDERS_TOPIC, groupId = "stock")
    public void onEvent(String orderDtoStr) throws JacksonException {
        OrderDto orderDto = validate(jsonMapper.readValue(orderDtoStr, OrderDto.class));
        log.info("Received Order: {}", orderDto);
        if ("NEW".equals(orderDto.status())) {
            orderManageService.reserve(orderDto);
        } else {
            orderManageService.confirm(orderDto);
        }
    }

    @KafkaListener(id = "products", topics = AppConstants.PRODUCT_TOPIC, groupId = "product")
    public void onSaveProductEvent(@Payload String productDtoStr) throws JacksonException {
        log.info("Received Product: {}", productDtoStr);
        ProductDto productDto = validate(jsonMapper.readValue(productDtoStr, ProductDto.class));
        productManageService.manage(productDto);
    }

    private <T> T validate(T object) {
        Set<ConstraintViolation<T>> violations = validator.validate(object);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
        return object;
    }

    @DltHandler
    public void dlt(String orderDto, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.error("Received dead-letter message : {} from topic {}", orderDto, topic);
    }
}
