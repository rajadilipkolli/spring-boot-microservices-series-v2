/* Licensed under Apache-2.0 2023 */
package com.example.orderservice.config;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

public interface MyTestContainers {

    @ServiceConnection @Container
    PostgreSQLContainer<?> POSTGRE_SQL_CONTAINER =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres").withTag("15.4-alpine"));

    @Container
    @ServiceConnection(name = "openzipkin/zipkin")
    GenericContainer<?> zipkinContainer =
            new GenericContainer(DockerImageName.parse("openzipkin/zipkin"));
}
