/***
<p>
    Licensed under MIT License Copyright (c) 2024 Raja Kolli.
</p>
***/

package com.example.inventoryservice.common;

import java.time.Duration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class NonSQLContainersConfig {

    @Bean
    @ServiceConnection(name = "openzipkin/zipkin")
    GenericContainer<?> zipkContainer() {
        return new GenericContainer<>(DockerImageName.parse("openzipkin/zipkin"))
                .withExposedPorts(9411)
                .withReuse(true)
                .waitingFor(Wait.forHttp("/health").forPort(9411))
                .withStartupTimeout(Duration.ofMinutes(2))
                .withStartupAttempts(3);
    }

    @Bean
    @ServiceConnection
    KafkaContainer kafkaContainer() {
        return new KafkaContainer(DockerImageName.parse("apache/kafka-native").withTag("4.1.0"))
                .withReuse(true);
    }
}
