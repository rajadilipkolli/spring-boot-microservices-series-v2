/***
<p>
    Licensed under MIT License Copyright (c) 2023-2024 Raja Kolli.
</p>
***/

package com.example.orderservice.config.kafka;

import static com.example.orderservice.utils.AppConstants.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

/**
 * Kafka configuration class focused on topic creation. Rest of the producer configuration is
 * handled through application.yml
 */
@Configuration(proxyBeanMethods = false)
@EnableKafka
class KafkaConfig {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Bean
    KafkaAdmin.NewTopics topics() {
        log.info(
                "Inside creating topics :{}, {}, {}, {}",
                ORDERS_TOPIC,
                PAYMENT_ORDERS_TOPIC,
                STOCK_ORDERS_TOPIC,
                RECOVER_DLQ_TOPIC);
        // streams needs topics to be created beforehand, so instead of delegating to kafkaAdmin to
        // create, manually creating
        return new KafkaAdmin.NewTopics(
                TopicBuilder.name(ORDERS_TOPIC).build(),
                TopicBuilder.name(PAYMENT_ORDERS_TOPIC).build(),
                TopicBuilder.name(STOCK_ORDERS_TOPIC).build(),
                TopicBuilder.name(RECOVER_DLQ_TOPIC).replicas(1).partitions(1).build());
    }
}
