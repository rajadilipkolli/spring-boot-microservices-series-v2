/***
<p>
    Licensed under MIT License Copyright (c) 2022-2026 Raja Kolli.
</p>
***/

package com.example.inventoryservice.config;

import com.example.common.dtos.OrderDto;
import com.example.inventoryservice.entities.ProcessedKafkaMessage;
import com.example.inventoryservice.model.payload.ProductDto;
import com.example.inventoryservice.repositories.ProcessedKafkaMessageRepository;
import com.example.inventoryservice.services.InventoryOrderManageService;
import com.example.inventoryservice.services.ProductManageService;
import com.example.inventoryservice.utils.AppConstants;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
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
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@EnableKafka
@Configuration(proxyBeanMethods = false)
class KafkaListenerConfig {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private final InventoryOrderManageService orderManageService;
    private final ProductManageService productManageService;
    private final ProcessedKafkaMessageRepository processedKafkaMessageRepository;
    private final JsonMapper jsonMapper;

    KafkaListenerConfig(
            InventoryOrderManageService orderManageService,
            ProductManageService productManageService,
            ProcessedKafkaMessageRepository processedKafkaMessageRepository,
            JsonMapper jsonMapper) {
        this.orderManageService = orderManageService;
        this.productManageService = productManageService;
        this.processedKafkaMessageRepository = processedKafkaMessageRepository;
        this.jsonMapper = jsonMapper;
    }

    // retries if processing of event fails
    @RetryableTopic(
            backOff = @BackOff(delay = 1000, multiplier = 2.0),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE)
    @KafkaListener(id = "orders", topics = AppConstants.ORDERS_TOPIC, groupId = "stock")
    @Transactional
    public void onEvent(OrderDto orderDto, @Header(KafkaHeaders.RECEIVED_KEY) Long messageKey) {

        String messageKeyValue = String.valueOf(messageKey);
        String topic = AppConstants.ORDERS_TOPIC;
        String consumerGroup = "stock";

        log.info(
                "Received Order: {}, messageKey: {}, topic: {}, consumerGroup: {}",
                orderDto,
                messageKeyValue,
                topic,
                consumerGroup);

        if (processedKafkaMessageRepository.existsByMessageKeyAndTopicAndConsumerGroup(
                messageKeyValue, topic, consumerGroup)) {

            log.info(
                    "Ignoring duplicate Kafka message: key={}, topic={}, consumerGroup={}",
                    messageKeyValue,
                    topic,
                    consumerGroup);
            return;
        }

        processedKafkaMessageRepository.save(
                new ProcessedKafkaMessage()
                        .setId(UUID.randomUUID())
                        .setMessageKey(messageKeyValue)
                        .setTopic(topic)
                        .setConsumerGroup(consumerGroup)
                        .setProcessedAt(OffsetDateTime.now(ZoneOffset.UTC)));

        if ("NEW".equals(orderDto.status())) {
            orderManageService.reserve(orderDto);
        } else {
            orderManageService.confirm(orderDto);
        }

        log.info(
                "Processed Kafka message successfully: key={}, topic={}, consumerGroup={}",
                messageKeyValue,
                topic,
                consumerGroup);
    }

    @KafkaListener(id = "products", topics = AppConstants.PRODUCT_TOPIC, groupId = "product")
    public void onSaveProductEvent(@Payload String productDto) throws JacksonException {
        log.info("Received Product: {}", productDto);
        productManageService.manage(jsonMapper.readValue(productDto, ProductDto.class));
    }

    @DltHandler
    public void dlt(OrderDto orderDto, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.error("Received dead-letter message : {} from topic {}", orderDto, topic);
    }
}
