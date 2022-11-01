/* Licensed under Apache-2.0 2022 */
package org.service.registry;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
class NamingServerApplicationTests {

    private static final int CONFIG_SERVER_INTERNAL_PORT = 8888;

    @Container
    static ConfigServerContainer configServerContainer =
            new ConfigServerContainer(
                    DockerImageName.parse("dockertmt/mmv2-config-server:0.0.1-SNAPSHOT"));

    static {
        configServerContainer.start();
    }

    private static class ConfigServerContainer extends GenericContainer<ConfigServerContainer> {
        public ConfigServerContainer(final DockerImageName dockerImageName) {
            super(dockerImageName);
            withExposedPorts(CONFIG_SERVER_INTERNAL_PORT);
        }
    }

    @DynamicPropertySource
    static void addApplicationProperties(DynamicPropertyRegistry propertyRegistry) {

        propertyRegistry.add(
                "spring.config.import",
                () ->
                        String.format(
                                "optional:configserver:http://%s:%d/",
                                configServerContainer.getHost(),
                                configServerContainer.getMappedPort(CONFIG_SERVER_INTERNAL_PORT)));
    }

    @Test
    void contextLoads() {
        assertThat(configServerContainer.isRunning()).isTrue();
    }
}
