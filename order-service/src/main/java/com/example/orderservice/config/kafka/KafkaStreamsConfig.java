/***
<p>
    Licensed under MIT License Copyright (c) 2023-2025 Raja Kolli.
</p>
***/

package com.example.orderservice.config.kafka;

import static com.example.orderservice.utils.AppConstants.ORDERS_STORE;
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
import org.apache.kafka.streams.kstream.Printed;
import org.apache.kafka.streams.kstream.StreamJoined;
import org.apache.kafka.streams.state.Stores;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.kafka.annotation.EnableKafkaStreams;
import org.springframework.kafka.config.StreamsBuilderFactoryBeanConfigurer;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.streams.RecoveringDeserializationExceptionHandler;
import org.springframework.kafka.support.serializer.JacksonJsonSerde;
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
    @Order(Ordered.HIGHEST_PRECEDENCE)
    StreamsBuilderFactoryBeanConfigurer configurer(
            DeadLetterPublishingRecoverer deadLetterPublishingRecoverer) {
        return factoryBean -> {
            factoryBean.setStateListener(
                    (newState, oldState) ->
                            log.info(
                                    "Kafka Streams state transition from {} to {}",
                                    oldState,
                                    newState));

            Properties streamsConfiguration = factoryBean.getStreamsConfiguration();
            Assert.notNull(streamsConfiguration, "streamsConfiguration must not be null");

            // Enhanced error handling
            streamsConfiguration.put(
                    StreamsConfig.DESERIALIZATION_EXCEPTION_HANDLER_CLASS_CONFIG,
                    RecoveringDeserializationExceptionHandler.class);
            streamsConfiguration.put(
                    RecoveringDeserializationExceptionHandler.KSTREAM_DESERIALIZATION_RECOVERER,
                    deadLetterPublishingRecoverer);

            // Performance and reliability optimizations
            streamsConfiguration.put(StreamsConfig.REQUEST_TIMEOUT_MS_CONFIG, "60000");
            streamsConfiguration.put(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, "1000");
            streamsConfiguration.put(
                    StreamsConfig.PROCESSING_GUARANTEE_CONFIG, StreamsConfig.EXACTLY_ONCE_V2);

            // Memory management
            streamsConfiguration.put(
                    StreamsConfig.STATESTORE_CACHE_MAX_BYTES_CONFIG, "10485760"); // 10MB

            // Enhanced monitoring
            streamsConfiguration.put(StreamsConfig.METRICS_RECORDING_LEVEL_CONFIG, "INFO");

            log.info("Kafka Streams configured with enhanced error handling and monitoring");
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
        Serde<@NonNull OrderDto> orderSerde = new JacksonJsonSerde<>(OrderDto.class);

        // Log important config information for troubleshooting
        log.info(
                "Starting Kafka Stream configuration. This might help diagnose Spring Boot 3.4.0 issues");

        KStream<Long, OrderDto> paymentStream =
                kafkaStreamBuilder.stream(
                        PAYMENT_ORDERS_TOPIC, Consumed.with(Serdes.Long(), orderSerde));

        paymentStream
                .join(
                        kafkaStreamBuilder.stream(STOCK_ORDERS_TOPIC),
                        orderManageService::confirm,
                        JoinWindows.ofTimeDifferenceWithNoGrace(Duration.ofSeconds(10)),
                        StreamJoined.with(Serdes.Long(), orderSerde, orderSerde))
                .peek((k, o) -> log.info("Output of Stream : {} for key :{}", o, k))
                .to(ORDERS_TOPIC);

        paymentStream.print(Printed.toSysOut());

        return paymentStream;
    }

    @Bean
    KTable<Long, OrderDto> kTable(StreamsBuilder streamsBuilder) {
        log.info("Inside fetching KTable values");
        JacksonJsonSerde<@NonNull OrderDto> orderSerde = new JacksonJsonSerde<>(OrderDto.class);

        // KTable naturally keeps only the latest value for each key
        KTable<Long, OrderDto> ordersTable =
                streamsBuilder.table(
                        ORDERS_TOPIC,
                        Consumed.with(Serdes.Long(), orderSerde),
                        Materialized.<Long, OrderDto>as(
                                        Stores.persistentKeyValueStore(ORDERS_STORE))
                                .withKeySerde(Serdes.Long())
                                .withValueSerde(orderSerde));

        // Add logging for visibility
        ordersTable
                .toStream()
                .peek((key, value) -> log.info("KTable entry for key {}: {}", key, value));

        return ordersTable;
    }
}
