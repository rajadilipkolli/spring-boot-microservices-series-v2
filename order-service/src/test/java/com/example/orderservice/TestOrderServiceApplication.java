/* Licensed under Apache-2.0 2023 */
package com.example.orderservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.devtools.restart.RestartScope;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestOrderServiceApplication {

    @Bean
    @ServiceConnection
    @RestartScope
    KafkaContainer kafkaContainer() {
        return new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka").withTag("7.4.1"))
                .withKraft();
    }

    @ServiceConnection
    @Bean
    PostgreSQLContainer<?> postgreSQLContainer() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres").withTag("15.4-alpine"));
    }

    @Bean
    @ServiceConnection(name = "openzipkin/zipkin")
    GenericContainer<?> zipkinContainer() {
        return new GenericContainer(DockerImageName.parse("openzipkin/zipkin"));
    }

    public static void main(String[] args) {
        SpringApplication.from(OrderServiceApplication::main)
                .with(TestOrderServiceApplication.class)
                .run(args);
    }
}
