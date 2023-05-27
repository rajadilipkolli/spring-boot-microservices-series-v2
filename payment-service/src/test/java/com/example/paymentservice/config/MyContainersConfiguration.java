/* Licensed under Apache-2.0 2023 */
package com.example.paymentservice.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
@ImportTestcontainers(MyTestContainers.class)
public class MyContainersConfiguration {
    private static final Integer CONFIG_SERVER_INTERNAL_PORT = 8888;

    static final GenericContainer<?> CONFIG_SERVER_CONTAINER =
            new GenericContainer<>(
                            DockerImageName.parse("dockertmt/mmv2-config-server-17:0.0.1-SNAPSHOT"))
                    .withEnv("SPRING_PROFILES_ACTIVE", "native")
                    .withExposedPorts(CONFIG_SERVER_INTERNAL_PORT);

    static {
        Startables.deepStart(CONFIG_SERVER_CONTAINER).join();
        System.setProperty(
                "spring.config.import",
                String.format(
                        "optional:configserver:http://%s:%d/",
                        CONFIG_SERVER_CONTAINER.getHost(),
                        CONFIG_SERVER_CONTAINER.getMappedPort(CONFIG_SERVER_INTERNAL_PORT)));
    }
}
