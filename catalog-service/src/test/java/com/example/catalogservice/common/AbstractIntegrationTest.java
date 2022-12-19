package com.example.catalogservice.common;

import static com.example.catalogservice.utils.AppConstants.PROFILE_TEST;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import com.example.catalogservice.utils.AppConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles({PROFILE_TEST})
@SpringBootTest(webEnvironment = RANDOM_PORT)
@AutoConfigureMockMvc
@EmbeddedKafka(
        topics = {AppConstants.KAFKA_TOPIC},
        brokerProperties = {"listeners=PLAINTEXT://localhost:39092", "port=39092"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractIntegrationTest extends ContainerInitializer {

    @Autowired protected MockMvc mockMvc;

    @Autowired protected ObjectMapper objectMapper;
    @Autowired private EmbeddedKafkaBroker embeddedKafkaBroker;
    @Autowired private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

    @BeforeAll
    public void setUpKafka() {
        // wait until the partitions are assigned
        for (MessageListenerContainer messageListenerContainer :
                kafkaListenerEndpointRegistry.getListenerContainers()) {
            ContainerTestUtils.waitForAssignment(
                    messageListenerContainer, embeddedKafkaBroker.getPartitionsPerTopic());
        }
    }
}
