/***
<p>
    Licensed under MIT License Copyright (c) 2025 Raja Kolli.
</p>
***/

package com.example.api.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springdoc.core.models.GroupedOpenApi;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springframework.beans.factory.annotation.Autowired;

class SwaggerConfigIntegrationTest extends AbstractIntegrationTest {

    @Autowired private List<GroupedOpenApi> groupedOpenApis;

    @Autowired private SwaggerUiConfigProperties swaggerUiConfigProperties;

    @Test
    void shouldConfigureSwaggerUi() {
        assertThat(swaggerUiConfigProperties)
                .isNotNull()
                .extracting(SwaggerUiConfigProperties::getUrls)
                .isNotNull();
    }

    @Test
    void shouldConfigureSwaggerUrlsForServices() {
        assertThat(swaggerUiConfigProperties.getUrls())
                .isNotEmpty()
                .extracting("name")
                .containsExactlyInAnyOrder(
                        "api-gateway", "order", "inventory", "catalog", "payment");

        // Verify URL patterns for services and api-gateway
        swaggerUiConfigProperties
                .getUrls()
                .forEach(
                        url ->
                                assertThat(url.getUrl())
                                        .matches(
                                                url.getName().equals("api-gateway")
                                                        ? "/v3/api-docs"
                                                        : "/"
                                                                + url.getName()
                                                                + "-service/v3/api-docs"));
    }

    @Test
    void shouldCreateGroupedOpenApis() {
        assertThat(groupedOpenApis)
                .isNotNull()
                .isNotEmpty()
                .extracting("group")
                .containsExactlyInAnyOrder("order", "inventory", "catalog", "payment");
    }

    @Test
    void shouldCreateGroupedOpenApisWithCorrectDisplayNames() {
        assertThat(groupedOpenApis)
                .isNotNull()
                .isNotEmpty()
                .extracting("displayName")
                .containsExactlyInAnyOrder(
                        "ORDER Service", "INVENTORY Service", "CATALOG Service", "PAYMENT Service");
    }

    @Test
    void shouldCreateGroupedOpenApisWithCorrectPathsToMatch() {
        assertThat(groupedOpenApis)
                .isNotNull()
                .isNotEmpty()
                .allSatisfy(
                        api ->
                                assertThat(api.getPathsToMatch())
                                        .contains("/" + api.getGroup() + "/**"));
    }

    @Test
    void shouldRedirectToSwaggerUI() {
        webTestClient
                .get()
                .uri("/")
                .exchange()
                .expectStatus()
                .isTemporaryRedirect()
                .expectHeader()
                .valueEquals("Location", "swagger-ui.html");
    }

    @Test
    void shouldServeOpenAPIEndpoint() {
        webTestClient
                .get()
                .uri("/v3/api-docs")
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .contentType("application/json")
                .expectBody()
                .jsonPath("$.info.title")
                .isEqualTo("api-gateway")
                .jsonPath("$.info.description")
                .isEqualTo("Documentation for all the Microservices in Demo Application")
                .jsonPath("$.info.version")
                .isEqualTo("v1");
    }

    @Test
    void shouldFilterServiceRoutesCorrectly() {
        // Test the private method logic by checking the results
        // Only routes ending with "-service" and not containing "actuator" should be included
        assertThat(groupedOpenApis)
                .extracting("group")
                .allMatch(group -> !((String) group).contains("actuator"))
                .allMatch(
                        group -> {
                            String groupName = (String) group;
                            return groupName.equals("api-gateway")
                                    || List.of("order", "inventory", "catalog", "payment")
                                            .contains(groupName);
                        });
    }
}
