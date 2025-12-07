/***
<p>
    Licensed under MIT License Copyright (c) 2021-2025 Raja Kolli.
</p>
***/

package com.example.orderservice.common;

import com.example.common.dtos.OrderDto;
import com.example.orderservice.repositories.OrderItemRepository;
import com.example.orderservice.repositories.OrderRepository;
import com.example.orderservice.services.OrderManageService;
import com.example.orderservice.services.OrderService;
import io.micrometer.observation.tck.TestObservationRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest
public abstract class AbstractIntegrationTest extends ContainerInitializer {

    @Autowired protected MockMvc mockMvc;

    @Autowired protected JsonMapper jsonMapper;

    @Autowired protected OrderService orderService;
    @Autowired protected OrderManageService orderManageService;

    @Autowired protected OrderRepository orderRepository;
    @Autowired protected OrderItemRepository orderItemRepository;

    @Autowired protected KafkaTemplate<Long, OrderDto> kafkaTemplate;

    @BeforeEach
    void setUpAbstractIntegrationTest() {
        // Ensure containers are started before tests run (idempotent)
        ensureContainersStarted();
    }

    @TestConfiguration
    static class ObservationTestConfiguration {
        @Bean
        TestObservationRegistry observationRegistry() {
            return TestObservationRegistry.create();
        }
    }
}
