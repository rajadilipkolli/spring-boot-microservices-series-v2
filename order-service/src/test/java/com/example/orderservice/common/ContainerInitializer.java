/***
<p>
    Licensed under MIT License Copyright (c) 2021-2023 Raja Kolli.
</p>
***/

package com.example.orderservice.common;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import org.mockserver.client.MockServerClient;
import org.mockserver.model.Header;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MockServerContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;

public class ContainerInitializer {

    protected static final MockServerContainer MOCK_SERVER_CONTAINER =
            new MockServerContainer(DockerImageName.parse("mockserver/mockserver:5.15.0"))
                    .withReuse(true);

    protected static MockServerClient mockServerClient;

    static {
        Startables.deepStart(MOCK_SERVER_CONTAINER).join();
    }

    @DynamicPropertySource
    static void addApplicationProperties(DynamicPropertyRegistry propertyRegistry) {
        propertyRegistry.add("application.catalog-service-url", MOCK_SERVER_CONTAINER::getEndpoint);
        mockServerClient =
                new MockServerClient(
                        MOCK_SERVER_CONTAINER.getHost(), MOCK_SERVER_CONTAINER.getServerPort());
    }

    protected static void mockProductsExistsRequest(boolean status, String... productCodes) {
        mockServerClient
                .when(
                        request()
                                .withMethod("GET")
                                .withPath("/api/catalog/exists")
                                .withQueryStringParameter("productCodes", productCodes))
                .respond(
                        response()
                                .withStatusCode(200)
                                .withHeaders(
                                        new Header(
                                                HttpHeaders.CONTENT_TYPE,
                                                MediaType.APPLICATION_JSON_VALUE))
                                .withBody(String.valueOf(status)));
    }
}
