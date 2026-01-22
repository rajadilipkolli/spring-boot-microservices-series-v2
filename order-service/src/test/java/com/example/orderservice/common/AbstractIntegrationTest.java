/***
<p>
    Licensed under MIT License Copyright (c) 2021-2026 Raja Kolli.
</p>
***/

package com.example.orderservice.common;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import com.example.common.dtos.OrderDto;
import com.example.orderservice.repositories.OrderItemRepository;
import com.example.orderservice.repositories.OrderRepository;
import com.example.orderservice.services.OrderManageService;
import com.example.orderservice.services.OrderService;
import com.example.orderservice.utils.AppConstants;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.micrometer.observation.tck.TestObservationRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.wiremock.spring.ConfigureWireMock;
import org.wiremock.spring.EnableWireMock;
import org.wiremock.spring.InjectWireMock;
import tools.jackson.databind.json.JsonMapper;

@ActiveProfiles({AppConstants.PROFILE_TEST})
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = {ContainersConfig.class, OrderServicePostGreSQLContainer.class})
@AutoConfigureMockMvc
@AutoConfigureTracing
@EnableWireMock(
        @ConfigureWireMock(
                name = "catalog-service",
                baseUrlProperties = "application.catalog-service-url"))
public abstract class AbstractIntegrationTest {

    @Autowired protected MockMvc mockMvc;

    @Autowired protected JsonMapper jsonMapper;

    @Autowired protected OrderService orderService;
    @Autowired protected OrderManageService orderManageService;

    @Autowired protected OrderRepository orderRepository;
    @Autowired protected OrderItemRepository orderItemRepository;

    @Autowired protected KafkaTemplate<Long, OrderDto> kafkaTemplate;

    @InjectWireMock("catalog-service")
    protected WireMockServer wireMockServer;

    @TestConfiguration
    static class ObservationTestConfiguration {
        @Bean
        TestObservationRegistry observationRegistry() {
            return TestObservationRegistry.create();
        }
    }

    public void mockProductsExistsRequest(boolean status, String... productCodes) {
        wireMockServer.resetAll();
        var mappingBuilder = get(urlPathEqualTo("/api/catalog/exists"));
        for (String code : productCodes) {
            mappingBuilder.withQueryParam("productCodes", equalTo(code));
        }
        wireMockServer.stubFor(
                mappingBuilder.willReturn(
                        aResponse()
                                .withHeader(
                                        HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                                .withBody(String.valueOf(status))));
    }
}
