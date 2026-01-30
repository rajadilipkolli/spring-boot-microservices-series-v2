/***
<p>
    Licensed under MIT License Copyright (c) 2021-2025 Raja Kolli.
</p>
***/

package com.example.api.gateway.config;

import com.example.api.gateway.filter.CorrelationIdFilterIntegrationTest;
import com.example.api.gateway.filter.LoggingFilterIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.junit.jupiter.Container;
import org.wiremock.integrations.testcontainers.WireMockContainer;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.cloud.discovery.reactive.enabled=false",
            "eureka.client.enabled=false",
            "spring.cloud.config.enabled=false"
        },
        classes = ContainerConfig.class)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    @Autowired protected WebTestClient webTestClient;

    @Container
    protected static final WireMockContainer wireMockServer =
            new WireMockContainer("wiremock/wiremock:latest-alpine")
                    .withMappingFromResource(
                            "order-by-id",
                            RateLimiterConfigurationIntegrationTest.class,
                            RateLimiterConfigurationIntegrationTest.class.getSimpleName()
                                    + "/mocks-config.json")
                    .withMappingFromResource(
                            "test-routing",
                            ApiGatewayConfigurationIntegrationTest.class,
                            ApiGatewayConfigurationIntegrationTest.class.getSimpleName()
                                    + "/test-routing.json")
                    .withMappingFromResource(
                            "get-mapping",
                            ApiGatewayConfigurationIntegrationTest.class,
                            ApiGatewayConfigurationIntegrationTest.class.getSimpleName()
                                    + "/get-mapping.json")
                    .withMappingFromResource(
                            "logging-test",
                            LoggingFilterIntegrationTest.class,
                            LoggingFilterIntegrationTest.class.getSimpleName()
                                    + "/logging-test.json")
                    .withMappingFromResource(
                            "correlation-test",
                            CorrelationIdFilterIntegrationTest.class,
                            CorrelationIdFilterIntegrationTest.class.getSimpleName()
                                    + "/correlation-test.json");

    static {
        wireMockServer.start();
    }
}
