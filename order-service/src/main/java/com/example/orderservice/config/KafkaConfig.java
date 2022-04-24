package com.example.orderservice.config;

import java.time.Duration;

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

import com.example.orderservice.entities.Order;
import com.example.orderservice.services.OrderManageService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Configuration(proxyBeanMethods = false)
@EnableKafkaStreams
@Slf4j
@RequiredArgsConstructor
public class KafkaConfig {

	private final OrderManageService orderManageService;

	@Bean
	public NewTopic orders() {
		return TopicBuilder.name("orders").partitions(3).compact().build();
	}

	@Bean
	public NewTopic paymentTopic() {
		return TopicBuilder.name("payment-orders").partitions(3).compact().build();
	}

	@Bean
	public NewTopic stockTopic() {
		return TopicBuilder.name("stock-orders").partitions(3).compact().build();
	}

	
	@Bean
    public KStream<Long, Order> stream(StreamsBuilder builder) {
        JsonSerde<Order> orderSerde = new JsonSerde<>(Order.class);
        KStream<Long, Order> stream = builder
                .stream("payment-orders", Consumed.with(Serdes.Long(), orderSerde));

        stream.join(
                        builder.stream("stock-orders"),
                        orderManageService::confirm,
                        JoinWindows.of(Duration.ofSeconds(10)),
                        StreamJoined.with(Serdes.Long(), orderSerde, orderSerde))
                .peek((k, o) -> log.info("Output: {}", o))
                .to("orders");

        return stream;
    }

    @Bean
    public KTable<Long, Order> table(StreamsBuilder builder) {
        KeyValueBytesStoreSupplier store =
                Stores.persistentKeyValueStore("orders");
        JsonSerde<Order> orderSerde = new JsonSerde<>(Order.class);
        KStream<Long, Order> stream = builder
                .stream("orders", Consumed.with(Serdes.Long(), orderSerde));
        return stream.toTable(Materialized.<Long, Order>as(store)
                .withKeySerde(Serdes.Long())
                .withValueSerde(orderSerde));
    }
}
