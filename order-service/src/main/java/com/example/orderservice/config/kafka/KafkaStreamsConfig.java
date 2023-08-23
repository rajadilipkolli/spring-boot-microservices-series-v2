/* Licensed under Apache-2.0 2023 */
package com.example.orderservice.config.kafka;

import static com.example.orderservice.utils.AppConstants.ORDERS_TOPIC;
import static com.example.orderservice.utils.AppConstants.PAYMENT_ORDERS_TOPIC;
import static com.example.orderservice.utils.AppConstants.STOCK_ORDERS_TOPIC;

import com.example.common.dtos.OrderDto;
import com.example.orderservice.services.OrderManageService;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Executor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
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
import org.apache.kafka.streams.state.KeyValueBytesStoreSupplier;
import org.apache.kafka.streams.state.Stores;
import org.springframework.boot.autoconfigure.kafka.KafkaConnectionDetails;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.source.InvalidConfigurationPropertyValueException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.kafka.annotation.EnableKafkaStreams;
import org.springframework.kafka.annotation.KafkaStreamsDefaultConfiguration;
import org.springframework.kafka.config.KafkaStreamsConfiguration;
import org.springframework.kafka.config.StreamsBuilderFactoryBeanConfigurer;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.streams.RecoveringDeserializationExceptionHandler;
import org.springframework.kafka.support.serializer.JsonSerde;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableKafkaStreams
@Slf4j
@RequiredArgsConstructor
public class KafkaStreamsConfig {

    private final OrderManageService orderManageService;

    @Bean
    StreamsBuilderFactoryBeanConfigurer configurer() {
        return factoryBean -> {
            factoryBean.setStateListener(
                    (newState, oldState) ->
                            log.info("State transition from {} to {} ", oldState, newState));
        };
    }

    @Bean(KafkaStreamsDefaultConfiguration.DEFAULT_STREAMS_CONFIG_BEAN_NAME)
    KafkaStreamsConfiguration defaultKafkaStreamsConfig(
            Environment environment,
            KafkaConnectionDetails connectionDetails,
            KafkaProperties kafkaProperties,
            DeadLetterPublishingRecoverer deadLetterPublishingRecoverer) {
        Map<String, Object> properties = kafkaProperties.buildStreamsProperties();
        properties.put(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                connectionDetails.getStreamsBootstrapServers());
        if (kafkaProperties.getStreams().getApplicationId() == null) {
            String applicationName = environment.getProperty("spring.application.name");
            if (applicationName == null) {
                throw new InvalidConfigurationPropertyValueException(
                        "spring.kafka.streams.application-id",
                        null,
                        "This property is mandatory and fallback 'spring.application.name' is not set either.");
            }
            properties.put(StreamsConfig.APPLICATION_ID_CONFIG, applicationName);
        }
        properties.put(
                StreamsConfig.DEFAULT_DESERIALIZATION_EXCEPTION_HANDLER_CLASS_CONFIG,
                RecoveringDeserializationExceptionHandler.class);
        properties.put(
                RecoveringDeserializationExceptionHandler.KSTREAM_DESERIALIZATION_RECOVERER,
                deadLetterPublishingRecoverer);
        return new KafkaStreamsConfiguration(properties);
    }

    @Bean
    DeadLetterPublishingRecoverer deadLetterPublishingRecoverer(
            ProducerFactory<byte[], byte[]> producerFactory) {
        return new DeadLetterPublishingRecoverer(
                new KafkaTemplate<>(producerFactory),
                (record, ex) -> new TopicPartition("recovererDLQ", -1));
    }

    @Bean
    KStream<Long, OrderDto> stream(StreamsBuilder kStreamBuilder) {
        Serde<OrderDto> orderSerde = new JsonSerde<>(OrderDto.class);
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
        stream.print(Printed.toSysOut());
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

    @Bean
    Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(5);
        executor.setThreadNamePrefix("kafkaSender-");
        executor.initialize();
        return executor;
    }
}
