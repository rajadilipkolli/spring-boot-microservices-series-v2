/***
<p>
    Licensed under MIT License Copyright (c) 2021-2025 Raja Kolli.
</p>
***/

package com.example.catalogservice.common;

import static com.example.catalogservice.utils.AppConstants.PROFILE_TEST;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import com.example.catalogservice.config.TestKafkaListenerConfig;
import com.example.catalogservice.repositories.ProductRepository;
import com.example.catalogservice.services.ProductService;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import tools.jackson.databind.json.JsonMapper;

@ActiveProfiles({PROFILE_TEST})
@SpringBootTest(
        webEnvironment = RANDOM_PORT,
        properties = {"spring.cloud.config.enabled=false"},
        classes = {SQLContainerConfig.class, TestKafkaListenerConfig.class, ContainersConfig.class})
@AutoConfigureWebTestClient
@AutoConfigureMetrics
public abstract class AbstractIntegrationTest {

    @Autowired protected WebTestClient webTestClient;

    @Autowired protected CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired protected JsonMapper jsonMapper;

    @Autowired protected ProductRepository productRepository;

    @Autowired protected ProductService productService;
}
