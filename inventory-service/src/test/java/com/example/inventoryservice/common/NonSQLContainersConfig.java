/***
<p>
    Licensed under MIT License Copyright (c) 2023-2025 Raja Kolli.
</p>
***/

package com.example.inventoryservice.common;

import java.time.Duration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.grafana.LgtmStackContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class NonSQLContainersConfig {

    @Bean
    @ServiceConnection
    LgtmStackContainer lgtmContainer() {
        return new LgtmStackContainer(DockerImageName.parse("grafana/otel-lgtm:0.16.0"))
                .withStartupTimeout(Duration.ofMinutes(2));
    }

    @Bean
    @ServiceConnection
    KafkaContainer kafkaContainer() {
        return new KafkaContainer(DockerImageName.parse("apache/kafka-native").withTag("4.1.1"))
                .withReuse(true);
    }
}
