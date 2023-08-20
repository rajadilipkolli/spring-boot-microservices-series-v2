/* Licensed under Apache-2.0 2022 */
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
        return new KafkaAdmin.NewTopics(
                TopicBuilder.name(ORDERS_TOPIC).partitions(3).replicas(1).build(),
                TopicBuilder.name(PAYMENT_ORDERS_TOPIC).partitions(3).replicas(1).build(),
                TopicBuilder.name(STOCK_ORDERS_TOPIC).partitions(3).replicas(1).build());
    }
}
