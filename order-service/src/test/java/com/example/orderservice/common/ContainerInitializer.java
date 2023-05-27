/* Licensed under Apache-2.0 2021-2023 */
package com.example.orderservice.common;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import org.mockserver.client.MockServerClient;
import org.mockserver.model.Header;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MockServerContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;

public class ContainerInitializer {
    private static final int CONFIG_SERVER_INTERNAL_PORT = 8888;

    protected static final MockServerContainer MOCK_SERVER_CONTAINER =
            new MockServerContainer(
                    DockerImageName.parse("mockserver/mockserver:mockserver-5.15.0"));

    static final ConfigServerContainer CONFIG_SERVER_CONTAINER =
            new ConfigServerContainer(
                            DockerImageName.parse("dockertmt/mmv2-config-server-17:0.0.1-SNAPSHOT"))
                    .withExposedPorts(CONFIG_SERVER_INTERNAL_PORT);

    protected static MockServerClient mockServerClient;

    static {
        Startables.deepStart(CONFIG_SERVER_CONTAINER, MOCK_SERVER_CONTAINER).join();
    }

    private static class ConfigServerContainer extends GenericContainer<ConfigServerContainer> {

        public ConfigServerContainer(final DockerImageName dockerImageName) {
            super(dockerImageName);
        }
    }

    @DynamicPropertySource
    static void addApplicationProperties(DynamicPropertyRegistry propertyRegistry) {
        propertyRegistry.add(
                "spring.config.import",
                () ->
                        String.format(
                                "optional:configserver:http://%s:%d/",
                                CONFIG_SERVER_CONTAINER.getHost(),
                                CONFIG_SERVER_CONTAINER.getMappedPort(
                                        CONFIG_SERVER_INTERNAL_PORT)));
        propertyRegistry.add("application.catalog-service-url", MOCK_SERVER_CONTAINER::getEndpoint);
        mockServerClient =
                new MockServerClient(
                        MOCK_SERVER_CONTAINER.getHost(), MOCK_SERVER_CONTAINER.getServerPort());
    }

    protected static void mockProductExistsRequest(boolean status) {
        mockServerClient
                .when(request().withMethod("GET").withPath("/api/catalog/exists/Product1"))
                .respond(
                        response()
                                .withStatusCode(200)
                                .withHeaders(
                                        new Header(
                                                "Content-Type", "application/json; charset=utf-8"))
                                .withBody(String.valueOf(status)));
    }
}
