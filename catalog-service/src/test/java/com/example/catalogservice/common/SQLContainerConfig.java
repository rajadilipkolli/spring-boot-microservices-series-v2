/***
<p>
    Licensed under MIT License Copyright (c) 2024 Raja Kolli.
</p>
***/

package com.example.catalogservice.common;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class SQLContainerConfig {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgreSQLContainer() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres").withTag("18-alpine"))
                .withDatabaseName("catalog-service")
                .withReuse(true);
    }
}
