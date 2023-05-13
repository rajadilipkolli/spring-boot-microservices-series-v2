package com.example.catalogservice.common;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

public interface MyContainers {

    @Container @ServiceConnection
    PostgreSQLContainer<?> postgreSqlContainer = new PostgreSQLContainer<>("postgres:15.3-alpine");

    @Container @ServiceConnection
    KafkaContainer kafkaContainer =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.0")).withKraft();

    @Container
    @ServiceConnection(name = "openzipkin/zipkin")
    GenericContainer<?> zipkinContainer =
            new GenericContainer<>(DockerImageName.parse("openzipkin/zipkin"));
}
