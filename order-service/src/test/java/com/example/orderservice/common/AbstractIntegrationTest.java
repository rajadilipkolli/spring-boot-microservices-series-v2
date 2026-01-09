/***
<p>
    Licensed under MIT License Copyright (c) 2021-2025 Raja Kolli.
</p>
***/

package com.example.orderservice.common;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import com.example.common.dtos.OrderDto;
import com.example.orderservice.repositories.OrderItemRepository;
import com.example.orderservice.repositories.OrderRepository;
import com.example.orderservice.services.OrderManageService;
import com.example.orderservice.services.OrderService;
import io.micrometer.observation.tck.TestObservationRegistry;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.Header;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.mockserver.MockServerContainer;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest
public abstract class AbstractIntegrationTest {

    @Autowired protected MockMvc mockMvc;

    @Autowired protected JsonMapper jsonMapper;

    @Autowired protected OrderService orderService;
    @Autowired protected OrderManageService orderManageService;

    @Autowired protected OrderRepository orderRepository;
    @Autowired protected OrderItemRepository orderItemRepository;

    @Autowired protected KafkaTemplate<Long, OrderDto> kafkaTemplate;

    @Autowired protected MockServerContainer mockServerContainer;

    @TestConfiguration
    static class ObservationTestConfiguration {
        @Bean
        TestObservationRegistry observationRegistry() {
            return TestObservationRegistry.create();
        }
    }

    public void mockProductsExistsRequest(boolean status, String... productCodes) {
        int attempts = 0;
        int maxAttempts = 5;
        long backoffMillis = 250L;

        while (true) {
            try {
                try (MockServerClient client =
                        new MockServerClient(
                                mockServerContainer.getHost(),
                                mockServerContainer.getServerPort())) {
                    client.when(
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
                break; // success
            } catch (Exception ex) {
                attempts++;
                if (attempts >= maxAttempts) {
                    throw ex;
                }
                try {
                    Thread.sleep(backoffMillis);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(ie);
                }
            }
        }
    }
}
