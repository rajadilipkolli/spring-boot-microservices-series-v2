/***
<p>
    Licensed under MIT License Copyright (c) 2021-2026 Raja Kolli.
</p>
***/

package com.example.api.gateway.config;

import com.example.api.gateway.filter.ObservabilityWebFilterIntegrationTest;
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
                            ObservabilityWebFilterIntegrationTest.class,
                            ObservabilityWebFilterIntegrationTest.class.getSimpleName()
                                    + "/logging-test.json")
                    .withMappingFromResource(
                            "correlation-test",
                            ObservabilityWebFilterIntegrationTest.class,
                            ObservabilityWebFilterIntegrationTest.class.getSimpleName()
                                    + "/correlation-test.json")
                    .withMappingFromResource(
                            "catalog-service-cache",
                            CacheAndTransformationIntegrationTest.class,
                            CacheAndTransformationIntegrationTest.class.getSimpleName()
                                    + "/catalog-service-cache.json")
                    .withMappingFromResource(
                            "catalog-service-cache-second",
                            CacheAndTransformationIntegrationTest.class,
                            CacheAndTransformationIntegrationTest.class.getSimpleName()
                                    + "/catalog-service-cache-second.json")
                    .withMappingFromResource(
                            "transform-service",
                            CacheAndTransformationIntegrationTest.class,
                            CacheAndTransformationIntegrationTest.class.getSimpleName()
                                    + "/transform-service.json");

    static {
        wireMockServer.start();
    }
}
