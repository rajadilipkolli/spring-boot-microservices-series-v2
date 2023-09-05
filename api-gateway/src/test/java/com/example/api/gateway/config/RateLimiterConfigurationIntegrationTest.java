/* Licensed under Apache-2.0 2023 */
package com.example.api.gateway.config;

import com.carrotsearch.junitbenchmarks.BenchmarkOptions;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.testcontainers.containers.MockServerContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

@Slf4j
public class RateLimiterConfigurationIntegrationTest extends AbstractIntegrationTest {

    @Container
    static MockServerContainer mockServer =
            new MockServerContainer(DockerImageName.parse("mockserver/mockserver:5.15.0"));

    private MockServerClient mockServerClient;

    @BeforeAll
    void setUp() {
        mockServer.start();
        System.setProperty("spring.cloud.gateway.routes[0].id", "order-service");
        System.setProperty(
                "spring.cloud.gateway.routes[0].uri",
                "http://" + mockServer.getHost() + ":" + mockServer.getServerPort());
        System.setProperty("spring.cloud.gateway.routes[0].predicates[0]", "Path=/order/**");
        System.setProperty(
                "spring.cloud.gateway.routes[0].filters[0]",
                "RewritePath=/order/(?<path>.*), /$\\{path}");
        System.setProperty("spring.cloud.gateway.routes[0].filters[1].name", "RequestRateLimiter");
        System.setProperty(
                "spring.cloud.gateway.routes[0].filters[1].args.redis-rate-limiter.replenishRate",
                "10");
        System.setProperty(
                "spring.cloud.gateway.routes[0].filters[1].args.redis-rate-limiter.burstCapacity",
                "20");
        System.setProperty(
                "spring.cloud.gateway.routes[0].filters[1].args.redis-rate-limiter.requestedTokens",
                "15");
        mockServerClient = new MockServerClient(mockServer.getHost(), mockServer.getServerPort());
    }

    @Test
    @BenchmarkOptions(warmupRounds = 0, concurrency = 6, benchmarkRounds = 600)
    public void testorderService() {
        mockServerClient
                .when(HttpRequest.request().withPath("/order/api/1"))
                .respond(
                        HttpResponse.response()
                                .withBody("{\"id\":1}")
                                .withHeader("Content-Type", "application/json"));
        EntityExchangeResult<String> r =
                webTestClient
                        .get()
                        .uri("/api/{id}", 1)
                        .accept(MediaType.APPLICATION_JSON)
                        .exchange()
                        .expectBody(String.class)
                        .returnResult();

        log.info(
                "Received: status->{}, payload->{}, remaining->{}",
                r.getStatus(),
                r.getResponseBody(),
                r.getResponseHeaders().get("X-RateLimit-Remaining"));
    }
}
