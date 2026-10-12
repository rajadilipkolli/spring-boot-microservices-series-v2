/***
<p>
    Licensed under MIT License Copyright (c) 2023-2026 Raja Kolli.
</p>
***/

package com.example.orderservice.config.kafka;

import static com.example.orderservice.utils.AppConstants.ORDERS_STORE;
import static com.example.orderservice.utils.AppConstants.ORDERS_TOPIC;
import static com.example.orderservice.utils.AppConstants.PAYMENT_ORDERS_TOPIC;
import static com.example.orderservice.utils.AppConstants.RECOVER_DLQ_TOPIC;
import static com.example.orderservice.utils.AppConstants.STOCK_ORDERS_TOPIC;

import com.example.orderservice.model.dtos.OrderDto;
import com.example.orderservice.services.OrderManageService;
import java.time.Duration;
import java.util.Map;
import java.util.Properties;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
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
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.StreamJoined;
import org.apache.kafka.streams.state.Stores;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.kafka.annotation.EnableKafkaStreams;
import org.springframework.kafka.config.StreamsBuilderFactoryBeanConfigurer;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
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

    /**
     * Configures Kafka Streams state logging, deserialization recovery, exactly-once processing,
     * timeouts, caching, and metrics.
     *
     * @param deadLetterPublishingRecoverer recoverer for records that fail deserialization
     * @return customizer applied to the Kafka Streams factory bean
     */
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
                    RecoveringDeserializationExceptionHandler.class.getName());
            streamsConfiguration.put(
                    RecoveringDeserializationExceptionHandler.RECOVERER,
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
    DeadLetterPublishingRecoverer deadLetterPublishingRecoverer(KafkaProperties properties) {
        Map<String, Object> props = properties.buildProducerProperties();
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);

        DefaultKafkaProducerFactory<byte[], byte[]> factory =
                new DefaultKafkaProducerFactory<>(props);

        return new DeadLetterPublishingRecoverer(
                new KafkaTemplate<>(factory),
                (record, ex) -> new TopicPartition(RECOVER_DLQ_TOPIC, -1));
    }

    @Bean
    Serde<@NonNull OrderDto> orderDtoSerde() {
        return new JacksonJsonSerde<>(OrderDto.class).noTypeInfo();
    }

    /**
     * Registers a join of payment and inventory responses for the same order and attempt, with a
     * maximum timestamp difference of 30 seconds and a 30-second grace period. Publishes non-null
     * confirmation results to the orders topic keyed by order ID.
     *
     * @return the original payment stream, before correlation and joining
     */
    @Bean
    KStream<String, OrderDto> stream(
            StreamsBuilder kafkaStreamBuilder, Serde<@NonNull OrderDto> orderSerde) {

        // Log important config information for troubleshooting
        log.info(
                "Starting Kafka Stream configuration. This might help diagnose Spring Boot 3.4.0 issues");

        KStream<String, OrderDto> paymentStream =
                kafkaStreamBuilder.stream(
                        PAYMENT_ORDERS_TOPIC, Consumed.with(Serdes.String(), orderSerde));

        correlateByAttempt(paymentStream)
                .join(
                        correlateByAttempt(
                                kafkaStreamBuilder.stream(
                                        STOCK_ORDERS_TOPIC,
                                        Consumed.with(Serdes.String(), orderSerde))),
                        orderManageService::confirm,
                        JoinWindows.ofTimeDifferenceAndGrace(
                                Duration.ofSeconds(30), Duration.ofSeconds(30)),
                        StreamJoined.with(Serdes.String(), orderSerde, orderSerde))
                .filter((key, order) -> order != null)
                .selectKey((key, order) -> order.orderId().toString())
                .peek((k, o) -> log.debug("Output of Stream : {} for key :{}", o, k))
                .to(ORDERS_TOPIC, Produced.with(Serdes.String(), orderSerde));

        paymentStream.print(Printed.toSysOut());

        return paymentStream;
    }

    /**
     * Drops null orders, missing order IDs, and null or blank attempt IDs, then keys the remaining
     * responses by {@code orderId:attemptId} for correlation.
     */
    private KStream<String, OrderDto> correlateByAttempt(KStream<String, OrderDto> stream) {
        // Legacy responses cannot be correlated safely; NEW orders will be retried with an ID.
        return stream.filter(
                        (key, order) ->
                                order != null
                                        && order.orderId() != null
                                        && order.attemptId() != null
                                        && !order.attemptId().isBlank())
                .selectKey((key, order) -> order.orderId() + ":" + order.attemptId());
    }

    @Bean
    KTable<String, OrderDto> kTable(
            StreamsBuilder streamsBuilder, Serde<@NonNull OrderDto> orderSerde) {
        log.info("Inside fetching KTable values");

        // KTable naturally keeps only the latest value for each key
        KTable<String, OrderDto> ordersTable =
                streamsBuilder.table(
                        ORDERS_TOPIC,
                        Consumed.with(Serdes.String(), orderSerde),
                        Materialized.<String, OrderDto>as(
                                        Stores.persistentKeyValueStore(ORDERS_STORE))
                                .withKeySerde(Serdes.String())
                                .withValueSerde(orderSerde));

        // Add logging for visibility
        ordersTable
                .toStream()
                .peek((key, value) -> log.debug("KTable entry for key {}: {}", key, value));

        return ordersTable;
    }
}
