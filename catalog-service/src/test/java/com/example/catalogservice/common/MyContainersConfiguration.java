/*** Licensed under Apache-2.0 2023 ***/
package com.example.catalogservice.common;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
@ImportTestcontainers(MyContainers.class)
public class MyContainersConfiguration {

    private static final Integer CONFIG_SERVER_INTERNAL_PORT = 8888;

    @Bean
    public GenericContainer configServerContainer(DynamicPropertyRegistry properties) {
        GenericContainer container =
                new GenericContainer(
                                DockerImageName.parse(
                                        "dockertmt/mmv2-config-server-17:0.0.1-SNAPSHOT"))
                        .withEnv("SPRING_PROFILES_ACTIVE", "native")
                        .withExposedPorts(CONFIG_SERVER_INTERNAL_PORT);
        properties.add(
                "spring.config.import",
                () ->
                        String.format(
                                "optional:configserver:http://%s:%d/",
                                container.getHost(),
                                container.getMappedPort(CONFIG_SERVER_INTERNAL_PORT)));
        return container;
    }
}
