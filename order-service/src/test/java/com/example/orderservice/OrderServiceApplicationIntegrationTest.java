package com.example.orderservice;

import java.util.List;

import com.example.orderservice.utils.AppConstants;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles({AppConstants.PROFILE_TEST, AppConstants.PROFILE_IT})
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class OrderServiceApplicationIntegrationTest {

    private static final int CONFIG_SERVER_INTERNAL_PORT = 8888;
    private static final int NAMING_SERVER_INTERNAL_PORT = 8761;

    @Container
    static NamingServerContainer namingServerContainer = new NamingServerContainer(DockerImageName.parse("dockertmt/mmv2-service-registry:0.0.1-SNAPSHOT"));

    @Container
    static ConfigServerContainer configServerContainer = new ConfigServerContainer(DockerImageName.parse("dockertmt/mmv2-config-server:0.0.1-SNAPSHOT"));

    static {
        Startables.deepStart(List.of( configServerContainer, namingServerContainer)).join();
    }

    private static class NamingServerContainer extends GenericContainer<NamingServerContainer> {

        public NamingServerContainer(final DockerImageName dockerImageName) {
            super(dockerImageName);
            withExposedPorts(NAMING_SERVER_INTERNAL_PORT);
        }
    }

    private static class ConfigServerContainer extends GenericContainer<ConfigServerContainer> {

        public ConfigServerContainer(final DockerImageName dockerImageName) {
            super(dockerImageName);
            withExposedPorts(CONFIG_SERVER_INTERNAL_PORT);
        }
    }

    @DynamicPropertySource
    static void addApplicationProperties(DynamicPropertyRegistry propertyRegistry) {

        propertyRegistry.add("spring.config.import", () ->
                String.format(
                        "optional:configserver:http://%s:%d/",
                        configServerContainer.getHost(),
                        configServerContainer.getMappedPort(CONFIG_SERVER_INTERNAL_PORT)
                )
        );
        propertyRegistry.add("eureka.client.serviceUrl.defaultZone", () ->
                String.format("http://%s:%d/eureka/",
                        namingServerContainer.getHost(),
                        namingServerContainer.getMappedPort(NAMING_SERVER_INTERNAL_PORT)
                )
        );
    }


    @Test
    void contextLoads() {
        assertThat(namingServerContainer.isRunning()).isTrue();
        assertThat(configServerContainer.isRunning()).isTrue();
    }

}
