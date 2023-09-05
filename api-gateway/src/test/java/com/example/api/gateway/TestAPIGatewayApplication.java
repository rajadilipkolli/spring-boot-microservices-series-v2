/* Licensed under Apache-2.0 2023 */
package com.example.api.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestAPIGatewayApplication {

    @Bean
    @ServiceConnection(name = "openzipkin/zipkin")
    GenericContainer<?> zipkinContainer() {
        return new GenericContainer<>(DockerImageName.parse("openzipkin/zipkin:latest"))
                .withExposedPorts(9411);
    }

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redisContainer() {
        return new GenericContainer<>(DockerImageName.parse("redis:7.2.0-alpine"))
                .withExposedPorts(6379);
    }

    public static void main(String[] args) {
        SpringApplication.from(APIGatewayApplication::main)
                .with(TestAPIGatewayApplication.class)
                .run(args);
    }
}
