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

import com.example.common.dtos.OrderDto;
import com.example.orderservice.services.OrderManageService;
import java.time.Duration;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.SerializationException;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.kafka.autoconfigure.KafkaConnectionDetails;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaStreams;
import org.springframework.kafka.annotation.KafkaStreamsDefaultConfiguration;
import org.springframework.kafka.config.KafkaStreamsConfiguration;
import org.springframework.kafka.config.StreamsBuilderFactoryBeanConfigurer;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.streams.RecoveringDeserializationExceptionHandler;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
@EnableKafkaStreams
class KafkaStreamsConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaStreamsConfig.class);

    private final OrderManageService orderManageService;

    KafkaStreamsConfig(OrderManageService orderManageService) {
        this.orderManageService = orderManageService;
    }

    @Bean(name = KafkaStreamsDefaultConfiguration.DEFAULT_STREAMS_CONFIG_BEAN_NAME)
    KafkaStreamsConfiguration streamsConfig(
            KafkaProperties kafkaProperties,
            KafkaConnectionDetails kafkaConnectionDetails,
            DeadLetterPublishingRecoverer deadLetterPublishingRecoverer) {
        Map<String, Object> props = kafkaProperties.buildStreamsProperties();

        // Force the bootstrap servers from the environment (crucial for tests)
        props.put(
                StreamsConfig.BOOTSTRAP_SERVERS_CONFIG,
                kafkaConnectionDetails.getBootstrapServers());

        props.put(StreamsConfig.RECEIVE_BUFFER_CONFIG, -1);
        props.put(
                StreamsConfig.SECURITY_PROTOCOL_CONFIG,
                kafkaConnectionDetails.getSecurityProtocol());

        // Deserialization Exception Handler configuration
        props.put(
                StreamsConfig.DESERIALIZATION_EXCEPTION_HANDLER_CLASS_CONFIG,
                RecoveringDeserializationExceptionHandler.class);
        props.put(
                RecoveringDeserializationExceptionHandler.KSTREAM_DESERIALIZATION_RECOVERER,
                deadLetterPublishingRecoverer);

        log.info(
                "Kafka Streams properties initialized with RecoveringDeserializationExceptionHandler and bootstrap servers: {}",
                kafkaConnectionDetails.getBootstrapServers());

        return new KafkaStreamsConfiguration(props);
    }

    @Bean
    StreamsBuilderFactoryBeanConfigurer configurer() {
        return factoryBean ->
                factoryBean.setStateListener(
                        (newState, oldState) ->
                                log.info(
                                        "Kafka Streams state transition from {} to {}",
                                        oldState,
                                        newState));
    }

    @Bean
    DeadLetterPublishingRecoverer deadLetterPublishingRecoverer(
            KafkaProperties kafkaProperties, KafkaConnectionDetails connectionDetails) {
        Map<String, Object> props = kafkaProperties.buildProducerProperties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, connectionDetails.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);

        DefaultKafkaProducerFactory<byte[], byte[]> factory =
                new DefaultKafkaProducerFactory<>(props);
        KafkaTemplate<byte[], byte[]> template = new KafkaTemplate<>(factory);

        return new DeadLetterPublishingRecoverer(
                template,
                (record, ex) -> {
                    log.info(
                            "Recoverer called for record: {} with exception: {}",
                            record,
                            ex.getMessage());
                    return new TopicPartition(RECOVER_DLQ_TOPIC, -1);
                });
    }

    @Bean
    public Serde<OrderDto> orderSerde(JsonMapper jsonMapper) {
        return Serdes.serdeFrom(
                (topic, data) -> {
                    try {
                        return jsonMapper.writeValueAsBytes(data);
                    } catch (Exception e) {
                        throw new SerializationException("Serialization failed", e);
                    }
                },
                (topic, data) -> {
                    if (data == null) {
                        return null;
                    }
                    try {
                        return jsonMapper.readValue(data, OrderDto.class);
                    } catch (Exception e) {
                        // Explicitly throw SerializationException to trigger the
                        // DeserializationExceptionHandler
                        log.debug("Deserialization failed for topic {}: {}", topic, e.getMessage());
                        throw new SerializationException("Deserialization failed", e);
                    }
                });
    }

    @Bean
    KStream<String, OrderDto> stream(
            StreamsBuilder kafkaStreamBuilder, Serde<OrderDto> orderSerde) {

        log.info("Initializing Kafka Stream with payment and stock topics");

        KStream<String, OrderDto> paymentStream =
                kafkaStreamBuilder.stream(
                        PAYMENT_ORDERS_TOPIC, Consumed.with(Serdes.String(), orderSerde));

        paymentStream
                .join(
                        kafkaStreamBuilder.stream(
                                STOCK_ORDERS_TOPIC, Consumed.with(Serdes.String(), orderSerde)),
                        orderManageService::confirm,
                        JoinWindows.ofTimeDifferenceWithNoGrace(Duration.ofSeconds(10)),
                        StreamJoined.with(Serdes.String(), orderSerde, orderSerde))
                .peek((k, o) -> log.debug("Output of Stream : {} for key :{}", o, k))
                .to(ORDERS_TOPIC, Produced.with(Serdes.String(), orderSerde));

        paymentStream.print(Printed.toSysOut());

        return paymentStream;
    }

    @Bean
    KTable<String, OrderDto> kTable(StreamsBuilder streamsBuilder, Serde<OrderDto> orderSerde) {
        log.info("Initializing KTable for orders store");

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
