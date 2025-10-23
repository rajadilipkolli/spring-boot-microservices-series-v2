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
import com.example.orderservice.utils.AppConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.tracing.test.simple.SimpleTracer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles({AppConstants.PROFILE_TEST})
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.cloud.config.enabled=false"},
        classes = {ContainersConfig.class, PostGreSQLContainer.class})
@AutoConfigureMockMvc
@AutoConfigureObservability
public abstract class AbstractIntegrationTest extends ContainerInitializer {

    @Autowired protected MockMvc mockMvc;

    @Autowired protected ObjectMapper objectMapper;

    @Autowired protected OrderService orderService;
    @Autowired protected OrderManageService orderManageService;

    @Autowired protected OrderRepository orderRepository;
    @Autowired protected OrderItemRepository orderItemRepository;

    @Autowired protected KafkaTemplate<Long, OrderDto> kafkaTemplate;

    @TestConfiguration
    static class ObservationTestConfiguration {
        @Bean
        TestObservationRegistry observationRegistry() {
            return TestObservationRegistry.create();
        }

        @Bean
        SimpleTracer simpleTracer() {
            return new SimpleTracer();
        }
    }
}
