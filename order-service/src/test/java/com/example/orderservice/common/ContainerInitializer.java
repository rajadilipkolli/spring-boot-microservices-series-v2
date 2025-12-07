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
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.mockserver.MockServerContainer;
import org.testcontainers.utility.DockerImageName;

public class ContainerInitializer {

    protected static final MockServerContainer MOCK_SERVER_CONTAINER =
            new MockServerContainer(DockerImageName.parse("mockserver/mockserver:5.15.0"))
                    .withReuse(true);

    private static MockServerClient mockServerClient;
    private static volatile boolean containersStarted = false;

    /**
     * Ensures containers are started exactly once using double-checked locking. This method is
     * idempotent and thread-safe.
     */
    protected static synchronized void ensureContainersStarted() {
        if (!containersStarted) {
            Startables.deepStart(MOCK_SERVER_CONTAINER).join();
            containersStarted = true;
        }
    }

    /**
     * Lazily initializes and returns the MockServerClient. Call ensureContainersStarted() before
     * using this method.
     */
    protected static synchronized MockServerClient getMockServerClient() {
        if (mockServerClient == null) {
            ensureContainersStarted();
            mockServerClient =
                    new MockServerClient(
                            MOCK_SERVER_CONTAINER.getHost(), MOCK_SERVER_CONTAINER.getServerPort());
        }
        return mockServerClient;
    }

    /** Resets all MockServer expectations to ensure test isolation. */
    protected static void resetMockServer() {
        getMockServerClient().reset();
    }

    @DynamicPropertySource
    static void addApplicationProperties(DynamicPropertyRegistry propertyRegistry) {
        ensureContainersStarted();
        propertyRegistry.add("application.catalog-service-url", MOCK_SERVER_CONTAINER::getEndpoint);
    }

    protected static void mockProductsExistsRequest(boolean status, String... productCodes) {
        getMockServerClient()
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
