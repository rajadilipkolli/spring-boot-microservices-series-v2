/* Licensed under Apache-2.0 2022 */
package com.example.orderservice.config;

import static com.example.orderservice.utils.AppConstants.*;

import com.example.common.dtos.OrderDto;
import com.example.orderservice.services.OrderManageService;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.JoinWindows;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.StreamJoined;
import org.apache.kafka.streams.state.KeyValueBytesStoreSupplier;
import org.apache.kafka.streams.state.Stores;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaStreams;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.support.serializer.JsonSerde;

@Configuration
@EnableKafkaStreams
@Slf4j
@RequiredArgsConstructor
public class KafkaConfig {

    private final OrderManageService orderManageService;

    @Bean
    KafkaAdmin.NewTopics topics() {
        log.info(
                "Inside creating topics :{}, {}, {}",
                ORDERS_TOPIC,
                PAYMENT_ORDERS_TOPIC,
                STOCK_ORDERS_TOPIC);
        return new KafkaAdmin.NewTopics(
                TopicBuilder.name(ORDERS_TOPIC).partitions(3).replicas(1).compact().build(),
                TopicBuilder.name(PAYMENT_ORDERS_TOPIC).partitions(3).replicas(1).compact().build(),
                TopicBuilder.name(STOCK_ORDERS_TOPIC).partitions(3).replicas(1).compact().build());
    }

    @Bean
    KStream<Long, OrderDto> stream(StreamsBuilder kStreamBuilder) {
        log.info("Inside stream Processing");
        JsonSerde<OrderDto> orderSerde = new JsonSerde<>(OrderDto.class);
        KStream<Long, OrderDto> stream =
                kStreamBuilder.stream(
                        PAYMENT_ORDERS_TOPIC, Consumed.with(Serdes.Long(), orderSerde));

        stream.join(
                        kStreamBuilder.stream(STOCK_ORDERS_TOPIC),
                        orderManageService::confirm,
                        JoinWindows.ofTimeDifferenceWithNoGrace(Duration.ofSeconds(10)),
                        StreamJoined.with(Serdes.Long(), orderSerde, orderSerde))
                .peek((k, o) -> log.info("Output of Stream : {} for key :{}", o, k))
                .to(ORDERS_TOPIC);

        return stream;
    }

    @Bean
    KTable<Long, OrderDto> table(StreamsBuilder builder) {
        log.info("Inside fetching KTable values");
        KeyValueBytesStoreSupplier store = Stores.persistentKeyValueStore(ORDERS_TOPIC);
        JsonSerde<OrderDto> orderSerde = new JsonSerde<>(OrderDto.class);
        KStream<Long, OrderDto> stream =
                builder.stream(ORDERS_TOPIC, Consumed.with(Serdes.Long(), orderSerde));
        return stream.toTable(
                Materialized.<Long, OrderDto>as(store)
                        .withKeySerde(Serdes.Long())
                        .withValueSerde(orderSerde));
    }
}
