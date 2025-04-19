/***
<p>
    Licensed under MIT License Copyright (c) 2024 Raja Kolli.
</p>
***/

package com.example.orderservice.config.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.common.dtos.OrderDto;
import com.example.orderservice.common.AbstractIntegrationTest;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.LongDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.kafka.KafkaConnectionDetails;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;

class KafkaStreamsConfigIntTest extends AbstractIntegrationTest {

    @Autowired private KafkaTemplate<Long, OrderDto> kafkaTemplate;
    @Autowired private KafkaTemplate<String, String> stringKafkaTemplate;

    private static Consumer<Long, String> dlqConsumer;

    @BeforeAll
    static void setup(@Autowired KafkaConnectionDetails connectionDetails) {
        dlqConsumer = buildTestConsumer(connectionDetails);
        dlqConsumer.subscribe(Collections.singletonList("recovererDLQ"));
    }

    private static Consumer<Long, String> buildTestConsumer(
            KafkaConnectionDetails connectionDetails) {
        var props = new HashMap<String, Object>();
        props.put(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                connectionDetails.getStreamsBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "deadletter-test-group");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, LongDeserializer.class.getName());
        props.put(
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 10);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true);
        props.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, 100);

        ConsumerFactory<Long, String> cf =
                new DefaultKafkaConsumerFactory<>(
                        props, new LongDeserializer(), new StringDeserializer());
        return cf.createConsumer("deadletter-test-consumer");
    }

    @Test
    void deadLetterPublishingRecoverer() throws Exception {
        // Clear any existing messages in the DLQ
        dlqConsumer.poll(Duration.ofMillis(100));

        // Method 1: Send a completely invalid JSON to the payment-orders topic
        // This will definitely cause a deserialization error in the streams processing
        String invalidJson = "{\"orderId\": \"THIS_SHOULD_BE_A_NUMBER\", \"badField\": true}";
        stringKafkaTemplate.send("payment-orders", invalidJson);

        // Method 2: Also try with a malformed OrderDto object
        OrderDto orderDto = new OrderDto();
        orderDto.setOrderId(-1L);
        orderDto.setCustomerId(-1L);
        orderDto.setSource("source");
        // Set status to a very long string to cause potential issues
        orderDto.setStatus(
                "INVALID_STATUS_THAT_IS_VERY_LONG_AND_SHOULD_CAUSE_PROBLEMS_WITH_DESERIALIZATION");
        orderDto.setItems(null);

        kafkaTemplate.send("payment-orders", 1L, orderDto);

        // Make sure both messages are sent
        kafkaTemplate.flush();
        stringKafkaTemplate.flush();

        // Wait longer for the message to be routed to the DLQ with better polling
        await().pollInterval(Duration.ofSeconds(1))
                .atMost(Duration.ofSeconds(60)) // Increase timeout to 60 seconds
                .untilAsserted(
                        () -> {
                            ConsumerRecords<Long, String> records =
                                    dlqConsumer.poll(Duration.ofSeconds(5));
                            // Print out all received records for debugging
                            if (records.count() > 0) {
                                records.forEach(
                                        record ->
                                                System.out.println(
                                                        "Found DLQ record: " + record.value()));
                            } else {
                                System.out.println("No DLQ records found in this poll attempt");
                            }

                            assertThat(records.count())
                                    .as("Invalid message should be routed to recovererDLQ")
                                    .isGreaterThan(0);
                        });
    }
}
