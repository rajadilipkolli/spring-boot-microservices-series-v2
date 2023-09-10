/***
<p>
    Licensed under MIT License Copyright (c) 2023 Raja Kolli.
</p>
***/

package com.example.api.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.RepeatedTest;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.testcontainers.containers.MockServerContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

@Slf4j
class RateLimiterConfigurationIntegrationTest extends AbstractIntegrationTest {

    @Container
    static MockServerContainer mockServer =
            new MockServerContainer(DockerImageName.parse("mockserver/mockserver:5.15.0"));

    private MockServerClient mockServerClient;

    @BeforeAll
    void setUp() {
        mockServer.start();
        String uri = "http://%s:%d".formatted(mockServer.getHost(), mockServer.getServerPort());
        System.setProperty("spring.cloud.gateway.routes[0].uri", uri);
        mockServerClient = new MockServerClient(mockServer.getHost(), mockServer.getServerPort());
    }

    @RepeatedTest(value = 25)
    void testOrderService() {
        mockServerClient
                .when(HttpRequest.request().withPath("/order-service/api/1"))
                .respond(
                        HttpResponse.response()
                                .withBody("{\"id\":1}")
                                .withHeader("Content-Type", "application/json"));
        EntityExchangeResult<String> r =
                webTestClient
                        .get()
                        .uri("/order-service/api/{id}", 1)
                        .accept(MediaType.APPLICATION_JSON)
                        .exchange()
                        .expectBody(String.class)
                        .returnResult();

        List<String> remainingToken = r.getResponseHeaders().get("X-RateLimit-Remaining");
        log.info(
                "Received: status->{}, payload->{}, remaining->{}",
                r.getStatus(),
                r.getResponseBody(),
                remainingToken);

        assertThat(r.getStatus().value()).isIn(404, 429);
        assertThat(remainingToken).isNotEmpty().hasSize(1);
    }
}
