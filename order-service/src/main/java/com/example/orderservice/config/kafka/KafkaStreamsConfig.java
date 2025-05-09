/***
<p>
    Licensed under MIT License Copyright (c) 2023 Raja Kolli.
</p>
***/

package com.example.orderservice.config.kafka;

import static com.example.orderservice.utils.AppConstants.ORDERS_TOPIC;
import static com.example.orderservice.utils.AppConstants.PAYMENT_ORDERS_TOPIC;
import static com.example.orderservice.utils.AppConstants.RECOVER_DLQ_TOPIC;
import static com.example.orderservice.utils.AppConstants.STOCK_ORDERS_TOPIC;

import com.example.common.dtos.OrderDto;
import com.example.orderservice.services.OrderManageService;
import java.time.Duration;
import java.util.Properties;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.JoinWindows;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.StreamJoined;
import org.apache.kafka.streams.state.KeyValueBytesStoreSupplier;
import org.apache.kafka.streams.state.Stores;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaStreams;
import org.springframework.kafka.config.StreamsBuilderFactoryBeanConfigurer;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.streams.RecoveringDeserializationExceptionHandler;
import org.springframework.kafka.support.serializer.JsonSerde;
import org.springframework.util.Assert;

@Configuration(proxyBeanMethods = false)
@EnableKafkaStreams
class KafkaStreamsConfig {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private final OrderManageService orderManageService;

    KafkaStreamsConfig(OrderManageService orderManageService) {
        this.orderManageService = orderManageService;
    }

    @Bean
    StreamsBuilderFactoryBeanConfigurer configurer(
            DeadLetterPublishingRecoverer deadLetterPublishingRecoverer) {
        return factoryBean -> {
            factoryBean.setStateListener(
                    (newState, oldState) ->
                            log.info("State transition from {} to {} ", oldState, newState));
            Properties streamsConfiguration = factoryBean.getStreamsConfiguration();
            Assert.notNull(streamsConfiguration, "streamsConfiguration must not be null");
            streamsConfiguration.put(
                    StreamsConfig.DEFAULT_DESERIALIZATION_EXCEPTION_HANDLER_CLASS_CONFIG,
                    RecoveringDeserializationExceptionHandler.class);
            streamsConfiguration.put(
                    RecoveringDeserializationExceptionHandler.KSTREAM_DESERIALIZATION_RECOVERER,
                    deadLetterPublishingRecoverer);
        };
    }

    @Bean
    DeadLetterPublishingRecoverer deadLetterPublishingRecoverer(
            ProducerFactory<byte[], byte[]> producerFactory) {
        return new DeadLetterPublishingRecoverer(
                new KafkaTemplate<>(producerFactory),
                (record, ex) -> new TopicPartition(RECOVER_DLQ_TOPIC, -1));
    }

    @Bean
    KStream<Long, OrderDto> stream(StreamsBuilder kafkaStreamBuilder) {
        Serde<OrderDto> orderSerde = new JsonSerde<>(OrderDto.class);

        // Add debug logging for all topics to diagnose message flow
        KStream<Long, OrderDto> ordersTopic =
                kafkaStreamBuilder.stream(ORDERS_TOPIC, Consumed.with(Serdes.Long(), orderSerde));
        ordersTopic.peek(
                (key, value) ->
                        log.info(
                                "ORDERS_TOPIC Received - key: {}, orderId: {}, status: {}",
                                key,
                                value != null ? value.getOrderId() : "null",
                                value != null ? value.getStatus() : "null"));

        KStream<Long, OrderDto> paymentStream =
                kafkaStreamBuilder.stream(
                        PAYMENT_ORDERS_TOPIC, Consumed.with(Serdes.Long(), orderSerde));

        // Enhanced debug logs for payment stream with more details
        paymentStream.peek(
                (key, value) -> {
                    log.info("PAYMENT_ORDERS_TOPIC Received - key: {}, value: {}", key, value);
                    if (value != null) {
                        log.info(
                                "PAYMENT_ORDERS_TOPIC Details - orderId: {}, status: {}, source: {}",
                                value.getOrderId(),
                                value.getStatus(),
                                value.getSource());
                    }
                });

        KStream<Long, OrderDto> stockStream =
                kafkaStreamBuilder.stream(
                        STOCK_ORDERS_TOPIC, Consumed.with(Serdes.Long(), orderSerde));

        // Enhanced debug logs for stock stream with more details
        stockStream.peek(
                (key, value) -> {
                    log.info("STOCK_ORDERS_TOPIC Received - key: {}, value: {}", key, value);
                    if (value != null) {
                        log.info(
                                "STOCK_ORDERS_TOPIC Details - orderId: {}, status: {}, source: {}",
                                value.getOrderId(),
                                value.getStatus(),
                                value.getSource());
                    }
                });

        // Log warning if no messages received in this topic after deployment
        log.warn(
                "Setting up join between PAYMENT_ORDERS_TOPIC and STOCK_ORDERS_TOPIC with 30 second window");

        // Increase join window to 30 seconds to ensure messages have more time to arrive
        KStream<Long, OrderDto> joinedStream =
                paymentStream.join(
                        stockStream,
                        orderManageService::confirm,
                        JoinWindows.ofTimeDifferenceWithNoGrace(Duration.ofSeconds(30)),
                        StreamJoined.with(Serdes.Long(), orderSerde, orderSerde));

        joinedStream
                .peek(
                        (k, o) ->
                                log.info(
                                        "Output of Stream JOIN: {} for key: {}, status: {}",
                                        o != null ? o.getOrderId() : "null",
                                        k,
                                        o != null ? o.getStatus() : "null"))
                .to(ORDERS_TOPIC);

        // Directly process and log each stream independently to see if they're receiving messages
        paymentStream.peek(
                (k, o) ->
                        log.info(
                                "PAYMENT stream independently - orderId: {}, status: {}",
                                o != null ? o.getOrderId() : "null",
                                o != null ? o.getStatus() : "null"));

        stockStream.peek(
                (k, o) ->
                        log.info(
                                "STOCK stream independently - orderId: {}, status: {}",
                                o != null ? o.getOrderId() : "null",
                                o != null ? o.getStatus() : "null"));

        return paymentStream;
    }

    @Bean
    KTable<Long, OrderDto> table(StreamsBuilder streamsBuilder) {
        log.info("Inside fetching KTable values");
        KeyValueBytesStoreSupplier store = Stores.persistentKeyValueStore(ORDERS_TOPIC);
        JsonSerde<OrderDto> orderSerde = new JsonSerde<>(OrderDto.class);
        KStream<Long, OrderDto> stream =
                streamsBuilder.stream(ORDERS_TOPIC, Consumed.with(Serdes.Long(), orderSerde));
        return stream.toTable(
                Materialized.<Long, OrderDto>as(store)
                        .withKeySerde(Serdes.Long())
                        .withValueSerde(orderSerde));
    }
}
