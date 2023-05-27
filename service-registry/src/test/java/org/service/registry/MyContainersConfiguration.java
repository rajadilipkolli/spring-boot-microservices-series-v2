/* Licensed under Apache-2.0 2023 */
package org.service.registry;

import org.springframework.boot.test.context.TestConfiguration;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
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
