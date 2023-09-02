/***
<p>
    Licensed under MIT License Copyright (c) 2023 Raja Kolli.
</p>
***/

package com.example.orderservice.config.kafka;

import static com.example.orderservice.utils.AppConstants.ORDERS_TOPIC;
import static com.example.orderservice.utils.AppConstants.PAYMENT_ORDERS_TOPIC;
import static com.example.orderservice.utils.AppConstants.STOCK_ORDERS_TOPIC;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

@Configuration
@EnableKafka
@Slf4j
@RequiredArgsConstructor
public class KafkaConfig {

    @Bean
    KafkaAdmin.NewTopics topics() {
        log.info(
                "Inside creating topics :{}, {}, {}",
                ORDERS_TOPIC,
                PAYMENT_ORDERS_TOPIC,
                STOCK_ORDERS_TOPIC);
        // streams needs topics to be created before hand, so instead of delegating to kafkaAdmin to
        // create, manually creating
        return new KafkaAdmin.NewTopics(
                TopicBuilder.name(ORDERS_TOPIC).build(),
                TopicBuilder.name(PAYMENT_ORDERS_TOPIC).build(),
                TopicBuilder.name(STOCK_ORDERS_TOPIC).build());
    }
}
