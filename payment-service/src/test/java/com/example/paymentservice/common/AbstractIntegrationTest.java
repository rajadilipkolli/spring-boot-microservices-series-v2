/*** Licensed under MIT License Copyright (c) 2021-2025 Raja Kolli. ***/
package com.example.paymentservice.common;

import static com.example.paymentservice.utils.AppConstants.PROFILE_TEST;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import com.example.common.dtos.OrderDto;
import com.example.paymentservice.repositories.CustomerRepository;
import com.example.paymentservice.services.listener.KafkaListenerConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

@ActiveProfiles({PROFILE_TEST})
@SpringBootTest(
        webEnvironment = RANDOM_PORT,
        properties = {"spring.cloud.config.enabled=false", "spring.cloud.discovery.enabled=false"},
        classes = {SQLContainerConfig.class, NonSQLContainerConfig.class})
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {

    @Autowired protected MockMvc mockMvc;

    @Autowired protected JsonMapper jsonMapper;

    @Autowired protected KafkaTemplate<Long, OrderDto> kafkaTemplate;

    @Autowired protected CustomerRepository customerRepository;

    @Autowired protected KafkaListenerConfig kafkaListenerConfig;
}
