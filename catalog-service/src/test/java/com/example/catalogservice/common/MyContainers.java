package com.example.catalogservice.common;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;

public interface MyContainers {

    @Container
    PostgreSQLContainer<?> postgreSqlContainer = new PostgreSQLContainer<>("postgres:15.2-alpine");
}
