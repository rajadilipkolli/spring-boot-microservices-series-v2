/* Licensed under Apache-2.0 2022 */
package com.example.orderservice.config;

import com.example.orderservice.dtos.OrderDto;
import com.example.orderservice.services.OrderManageService;
import java.time.Duration;

import com.example.orderservice.utils.AppConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
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
import org.springframework.kafka.support.serializer.JsonSerde;

@Configuration(proxyBeanMethods = false)
@EnableKafkaStreams
@Slf4j
@RequiredArgsConstructor
public class KafkaConfig {

    private final OrderManageService orderManageService;

    @Bean
    public NewTopic ordersTopic() {
        return TopicBuilder.name(AppConstants.ORDERS_TOPIC).partitions(3).compact().build();
    }

    @Bean
    public NewTopic paymentTopic() {
        return TopicBuilder.name(AppConstants.PAYMENT_ORDERS_TOPIC).partitions(3).compact().build();
    }

    @Bean
    public NewTopic stockTopic() {
        return TopicBuilder.name(AppConstants.STOCK_ORDERS_TOPIC).partitions(3).compact().build();
    }

    @Bean
    public KStream<Long, OrderDto> stream(StreamsBuilder builder) {
        JsonSerde<OrderDto> orderSerde = new JsonSerde<>(OrderDto.class);
        KStream<Long, OrderDto> stream =
                builder.stream(AppConstants.PAYMENT_ORDERS_TOPIC, Consumed.with(Serdes.Long(), orderSerde));

        stream.join(
                        builder.stream(AppConstants.STOCK_ORDERS_TOPIC),
                        orderManageService::confirm,
                        JoinWindows.ofTimeDifferenceWithNoGrace(Duration.ofSeconds(10)),
                        StreamJoined.with(Serdes.Long(), orderSerde, orderSerde))
                .peek((k, o) -> log.info("Output of Steam: {}", o))
                .to(AppConstants.ORDERS_TOPIC);

        return stream;
    }

    @Bean
    public KTable<Long, OrderDto> table(StreamsBuilder builder) {
        KeyValueBytesStoreSupplier store = Stores.persistentKeyValueStore(AppConstants.ORDERS_TOPIC);
        JsonSerde<OrderDto> orderSerde = new JsonSerde<>(OrderDto.class);
        KStream<Long, OrderDto> stream =
                builder.stream(AppConstants.ORDERS_TOPIC, Consumed.with(Serdes.Long(), orderSerde));
        return stream.toTable(
                Materialized.<Long, OrderDto>as(store)
                        .withKeySerde(Serdes.Long())
                        .withValueSerde(orderSerde));
    }
}
