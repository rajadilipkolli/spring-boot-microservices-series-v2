/*** Licensed under Apache-2.0 2023 ***/
package com.example.catalogservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestCatalogServiceApplication {

    @Bean
    @ServiceConnection(name = "openzipkin/zipkin")
    GenericContainer<?> zipkinContainer() {
        return new GenericContainer<>(DockerImageName.parse("openzipkin/zipkin"));
    }

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgreSqlContainer() {
        return new PostgreSQLContainer<>("postgres:15.3-alpine");
    }

    @Bean
    @ServiceConnection
    KafkaContainer kafkaContainer() {
        return new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.1")).withKraft();
    }

    public static void main(String[] args) {
        SpringApplication.from(CatalogServiceApplication::main)
                .with(TestCatalogServiceApplication.class)
                .run(args);
    }
}
