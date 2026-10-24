/***
<p>
    Licensed under MIT License Copyright (c) 2022-2026 Raja Kolli.
</p>
***/

package com.example.inventoryservice.inventory.config;

import com.example.common.dto.OrderDto;
import com.example.common.dto.ProductDto;
import com.example.inventoryservice.inventory.InventoryAPI;
import com.example.inventoryservice.inventory.utils.AppConstants;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.util.Set;
import org.apache.kafka.clients.consumer.ConsumerRecord;
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
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@EnableKafka
@Configuration(proxyBeanMethods = false)
class KafkaListenerConfig {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private final InventoryAPI inventoryAPI;
    private final JsonMapper jsonMapper;
    private final Validator validator;

    /** Creates the Kafka adapter with the inventory API, JSON mapper, and payload validator. */
    KafkaListenerConfig(InventoryAPI inventoryAPI, JsonMapper jsonMapper, Validator validator) {
        this.inventoryAPI = inventoryAPI;
        this.jsonMapper = jsonMapper;
        this.validator = validator;
    }

    /**
     * Validates the order JSON in the record value and reserves inventory for {@code NEW} orders;
     * other statuses are passed to confirmation handling. Processing failures propagate to the
     * Kafka listener's retry handling.
     *
     * @throws JacksonException if the record value cannot be deserialized as an order
     * @throws ConstraintViolationException if the order violates bean validation constraints
     */
    // retries if processing of event fails
    @RetryableTopic(
            backOff = @BackOff(delay = 1000, multiplier = 2.0),
            retryTopicSuffix = "-retry-inventory",
            dltTopicSuffix = "-dlt-inventory",
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE)
    @KafkaListener(id = "inventory-orders", topics = AppConstants.ORDERS_TOPIC, groupId = "stock")
    public void onEvent(ConsumerRecord<String, String> record) throws JacksonException {
        String orderDtoStr = record.value();
        OrderDto orderDto = validate(jsonMapper.readValue(orderDtoStr, OrderDto.class));
        log.info("Received Order: {}", orderDto);
        if ("NEW".equals(orderDto.status())) {
            inventoryAPI.reserve(orderDto);
        } else {
            inventoryAPI.confirm(orderDto);
        }
    }

    /**
     * Validates the product JSON in the record value and creates inventory if the product code is
     * absent. Existing products, including concurrent duplicate creation, are ignored; other
     * persistence failures propagate to the listener container.
     *
     * @throws JacksonException if the record value cannot be deserialized as a product
     * @throws ConstraintViolationException if the product violates bean validation constraints
     */
    @RetryableTopic(
            backOff = @BackOff(delay = 1000, multiplier = 2.0),
            retryTopicSuffix = "-retry-product",
            dltTopicSuffix = "-dlt-product",
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE)
    @KafkaListener(id = "products", topics = AppConstants.PRODUCT_TOPIC, groupId = "product")
    public void onSaveProductEvent(ConsumerRecord<String, String> record) throws JacksonException {
        String productDtoStr = record.value();
        log.info("Received Product: {}", productDtoStr);
        ProductDto productDto = validate(jsonMapper.readValue(productDtoStr, ProductDto.class));
        inventoryAPI.manage(productDto);
    }

    private <T> T validate(T object) {
        Set<ConstraintViolation<T>> violations = validator.validate(object);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
        return object;
    }

    /** Reports an order message delivered to the dead-letter topic for diagnosis. */
    @DltHandler
    public void dlt(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.error("Received dead-letter message : {} from topic {}", record.value(), topic);
    }
}
