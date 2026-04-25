/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.orderservice.config.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.common.dtos.OrderDto;
import com.example.orderservice.common.AbstractIntegrationTest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.LongDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.streams.KafkaStreams;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.kafka.autoconfigure.KafkaConnectionDetails;
import org.springframework.kafka.config.StreamsBuilderFactoryBean;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;

class KafkaStreamsConfigIntTest extends AbstractIntegrationTest {

    @Autowired private KafkaTemplate<String, OrderDto> kafkaTemplate;
    @Autowired private KafkaTemplate<String, String> stringKafkaTemplate;
    @Autowired private StreamsBuilderFactoryBean streamsBuilderFactoryBean;

    private static Consumer<String, String> dlqConsumer;

    @BeforeAll
    static void setup(@Autowired KafkaConnectionDetails connectionDetails) {
        dlqConsumer = buildTestConsumer(connectionDetails);
        dlqConsumer.subscribe(Collections.singletonList("recovererDLQ"));
    }

    @BeforeEach
    void waitForStreams() {
        await().atMost(Duration.ofSeconds(30))
                .until(
                        () ->
                                streamsBuilderFactoryBean.getKafkaStreams() != null
                                        && streamsBuilderFactoryBean
                                                .getKafkaStreams()
                                                .state()
                                                .equals(KafkaStreams.State.RUNNING));
    }

    @AfterAll
    static void cleanup() {
        if (dlqConsumer != null) {
            dlqConsumer.close();
        }
    }

    private static Consumer<String, String> buildTestConsumer(
            KafkaConnectionDetails connectionDetails) {
        var props = new HashMap<String, Object>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, connectionDetails.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "deadletter-test-group-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, LongDeserializer.class.getName());
        props.put(
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 10);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true);
        props.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, 100);

        ConsumerFactory<String, String> cf =
                new DefaultKafkaConsumerFactory<>(
                        props, new StringDeserializer(), new StringDeserializer());
        return cf.createConsumer("deadletter-test-consumer");
    }

    @Test
    void deadLetterPublishingRecoverer() {
        // Clear any existing messages in the DLQ
        dlqConsumer.poll(Duration.ofMillis(100));

        // Method 1: Send a completely invalid payload (not even JSON)
        // This will definitely cause a deserialization error in the streams processing
        stringKafkaTemplate.send("payment-orders", "NOT_A_JSON_PAYLOAD");

        // Method 2: Also try with a malformed OrderDto object
        // This will fail deserialization because customerId is expected to be a number
        // but we'll
        // send something else if we use string template
        String invalidDtoJson =
                "{\"orderId\": 1, \"customerId\": \"INVALID\", \"status\": \"NEW\", \"source\": \"test\"}";
        stringKafkaTemplate.send("payment-orders", invalidDtoJson);

        // Make sure messages are sent
        stringKafkaTemplate.flush();

        // Wait for messages to be routed to the DLQ
        List<ConsumerRecord<String, String>> dlqRecords = new ArrayList<>();
        await().atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofSeconds(1))
                .untilAsserted(
                        () -> {
                            dlqConsumer.poll(Duration.ofSeconds(1)).forEach(dlqRecords::add);
                            assertThat(dlqRecords)
                                    .as("Invalid messages should be routed to recovererDLQ")
                                    .hasSizeGreaterThanOrEqualTo(1);
                        });

        // Verify content of the DLQ message
        assertThat(dlqRecords.get(0).value()).contains("NOT_A_JSON_PAYLOAD");
    }
}
