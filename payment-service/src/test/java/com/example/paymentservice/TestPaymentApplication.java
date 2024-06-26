/*** Licensed under MIT License Copyright (c) 2023-2024 Raja Kolli. ***/
package com.example.paymentservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestPaymentApplication {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgreSQLContainer() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres").withTag("16-alpine"))
                .withReuse(true);
    }

    @Bean
    @ServiceConnection(name = "openzipkin/zipkin")
    GenericContainer<?> zipkContainer() {
        return new GenericContainer<>(DockerImageName.parse("openzipkin/zipkin"))
                .withExposedPorts(9411)
                .withReuse(true);
    }

    @Bean
    @ServiceConnection
    KafkaContainer kafkaContainer() {
        return new KafkaContainer(DockerImageName.parse("apache/kafka").withTag("3.7.0"))
                .withReuse(true);
    }

    public static void main(String[] args) {
        System.setProperty("spring.profiles.active", "local");
        SpringApplication.from(PaymentApplication::main)
                .with(TestPaymentApplication.class)
                .run(args);
    }
}
