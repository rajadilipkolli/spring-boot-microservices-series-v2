/***
<p>
    Licensed under MIT License Copyright (c) 2023-2024 Raja Kolli.
</p>
***/

package com.example.catalogservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestCatalogServiceApplication {

    @Bean
    @ServiceConnection(name = "openzipkin/zipkin")
    GenericContainer<?> zipkinContainer() {
        return new GenericContainer<>(DockerImageName.parse("openzipkin/zipkin:latest"))
                .withExposedPorts(9411)
                .withReuse(true);
    }

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgreSqlContainer() {
        return new PostgreSQLContainer<>("postgres:16-alpine").withReuse(true);
    }

    @Bean
    @ServiceConnection
    KafkaContainer kafkaContainer(DynamicPropertyRegistry dynamicPropertyRegistry) {
        KafkaContainer kafkaContainer =
                new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka").withTag("7.6.0"))
                        .withKraft()
                        .withReuse(true);
        dynamicPropertyRegistry.add(
                "spring.cloud.stream.kafka.binder.brokers", kafkaContainer::getBootstrapServers);
        return kafkaContainer;
    }

    public static void main(String[] args) {
        System.setProperty("spring.profiles.active", "test");
        SpringApplication.from(CatalogServiceApplication::main)
                .with(TestCatalogServiceApplication.class)
                .run(args);
    }
}
