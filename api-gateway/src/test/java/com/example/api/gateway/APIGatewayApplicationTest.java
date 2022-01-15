package com.example.api.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class APIGatewayApplicationTest {

    private static final int CONFIG_SERVER_INTERNAL_PORT = 8888;
    private static final int NAMING_SERVER_INTERNAL_PORT = 8761;
    private static final DockerImageName mongoDBDockerImageName = DockerImageName.parse("mongo");
//    private static final DockerImageName rabbitMQDockerImageName = DockerImageName.parse("rabbitmq:3.9.12-management");

    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer(mongoDBDockerImageName);

//    @Container
//    static RabbitMQContainer rabbitMQContainer = new RabbitMQContainer(rabbitMQDockerImageName);

    @Container
    static NamingServerContainer namingServerContainer = new NamingServerContainer(DockerImageName.parse("dockertmt/mmv2-service-registry:0.0.1-SNAPSHOT"));

    @Container
    static ConfigServerContainer configServerContainer = new ConfigServerContainer(DockerImageName.parse("dockertmt/mmv2-config-server:0.0.1-SNAPSHOT"));

    static {
        Startables.deepStart(List.of(mongoDBContainer, configServerContainer, namingServerContainer
//                ,rabbitMQContainer
        )).join();
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
        propertyRegistry.add("spring.data.mongodb.uri", () -> mongoDBContainer.getReplicaSetUrl());

        propertyRegistry.add("spring.config.import", () ->
                String.format(
                        "optional:configserver:http://%s:%d/",
                        configServerContainer.getContainerIpAddress(),
                        configServerContainer.getMappedPort(CONFIG_SERVER_INTERNAL_PORT)
                )
        );
        propertyRegistry.add("eureka.client.serviceUrl.defaultZone", () ->
                String.format("http://%s:%d/eureka/",
                        namingServerContainer.getContainerIpAddress(),
                        namingServerContainer.getMappedPort(NAMING_SERVER_INTERNAL_PORT)
                )
        );
    }

    @Test
    void contextLoads() {
        assertThat(mongoDBContainer.isRunning()).isTrue();
//        assertThat(rabbitMQContainer.isRunning()).isTrue();
    }

}
