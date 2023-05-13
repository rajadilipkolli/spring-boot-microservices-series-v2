package com.example.catalogservice.common;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;

public interface MyContainers {

    @Container @ServiceConnection
    PostgreSQLContainer<?> postgreSqlContainer = new PostgreSQLContainer<>("postgres:15.3-alpine");

    @Container @ServiceConnection
    KafkaContainer kafkaContainer =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.0")).withKraft();
}
