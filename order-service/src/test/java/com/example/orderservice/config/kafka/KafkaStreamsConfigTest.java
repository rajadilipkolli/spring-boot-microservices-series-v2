/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.orderservice.config.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.example.orderservice.model.dtos.OrderDto;
import com.example.orderservice.services.OrderManageService;
import com.example.orderservice.utils.AppConstants;
import java.util.List;
import java.util.Properties;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TopologyTestDriver;
import org.junit.jupiter.api.Test;

class KafkaStreamsConfigTest {
    @Test
    void joinsOnlyMatchingAttemptsAndRestoresOrderKey() {
        var manager = mock(OrderManageService.class);
        var config = new KafkaStreamsConfig(manager);
        var builder = new StreamsBuilder();
        try (var serde = config.orderDtoSerde()) {
            config.stream(builder, serde);
            var properties = new Properties();
            properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "attempt-test");
            properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:9092");
            try (var driver = new TopologyTestDriver(builder.build(), properties)) {
                var payments =
                        driver.createInputTopic(
                                AppConstants.PAYMENT_ORDERS_TOPIC,
                                Serdes.String().serializer(),
                                serde.serializer());
                var stocks =
                        driver.createInputTopic(
                                AppConstants.STOCK_ORDERS_TOPIC,
                                Serdes.String().serializer(),
                                serde.serializer());
                var orders =
                        driver.createOutputTopic(
                                AppConstants.ORDERS_TOPIC,
                                Serdes.String().deserializer(),
                                serde.deserializer());
                var payment = new OrderDto(1L, 2L, "ACCEPT", "PAYMENT", List.of(), "first");
                var stock = payment.withStatusAndSource("ACCEPT", "INVENTORY");
                payments.pipeInput("1", payment);
                stocks.pipeInput("1", stock.withAttemptId("second"));
                stocks.pipeInput("1", stock.withAttemptId(null));
                payments.pipeInput("1", payment.withAttemptId(null));
                assertThat(orders.isEmpty()).isTrue();
                verifyNoInteractions(manager);

                when(manager.confirm(payment, stock))
                        .thenReturn(stock.withStatusAndSource("CONFIRMED", null));
                stocks.pipeInput("1", stock);
                var result = orders.readKeyValue();
                assertThat(result.key).isEqualTo("1");
                assertThat(result.value.attemptId()).isEqualTo("first");
                verify(manager).confirm(payment, stock);
                assertThat(orders.isEmpty()).isTrue();
            }
        }
    }
}
