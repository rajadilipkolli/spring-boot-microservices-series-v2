/***
<p>
    Licensed under MIT License Copyright (c) 2023-2025 Raja Kolli.
</p>
***/

package com.example.orderservice.common;

import java.time.Duration;
import org.mockserver.client.MockServerClient;
import org.springframework.boot.devtools.restart.RestartScope;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.grafana.LgtmStackContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.mockserver.MockServerContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class ContainersConfig {

    @Bean
    @ServiceConnection
    @RestartScope
    KafkaContainer kafkaContainer() {
        return new KafkaContainer(DockerImageName.parse("apache/kafka-native").withTag("4.1.1"))
                .withReuse(true);
    }

    @Bean
    @ServiceConnection
    @RestartScope
    LgtmStackContainer lgtmContainer() {
        return new LgtmStackContainer(DockerImageName.parse("grafana/otel-lgtm:0.14.0"))
                .withStartupTimeout(Duration.ofMinutes(2));
    }

    @Bean
    MockServerContainer mockServerContainer() {
        MockServerContainer container =
                new MockServerContainer(DockerImageName.parse("mockserver/mockserver:5.15.0"))
                        .withReuse(true)
                        // wait until the server is listening on the port
                        .waitingFor(
                                Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(30)));
        // Ensure the container is started so its port is available when MockServerClient is created
        container.start();
        return container;
    }

    @Bean
    DynamicPropertyRegistrar dynamicPropertyRegistrar(MockServerContainer mockServerContainer) {
        return registry ->
                registry.add("application.catalog-service-url", mockServerContainer::getEndpoint);
    }

    @Bean
    @Lazy
    MockServerClient mockServerClient(MockServerContainer mockServerContainer) {
        return new MockServerClient(
                mockServerContainer.getHost(), mockServerContainer.getServerPort());
    }
}
