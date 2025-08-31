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
        assertThat(swaggerUiConfigProperties).isNotNull();
        assertThat(swaggerUiConfigProperties.getUrls()).isNotNull();
    }

    @Test
    void shouldConfigureSwaggerUrlsForServices() {
        assertThat(swaggerUiConfigProperties.getUrls()).isNotEmpty();

        // Verify that SwaggerUrls are configured for each service including api-gateway
        assertThat(swaggerUiConfigProperties.getUrls())
                .extracting("name")
                .containsExactlyInAnyOrder(
                        "api-gateway", "order", "inventory", "catalog", "payment");

        // Verify that each service SwaggerUrl has correct URL pattern
        swaggerUiConfigProperties.getUrls().stream()
                .filter(url -> !url.getName().equals("api-gateway"))
                .forEach(
                        url -> {
                            assertThat(url.getUrl())
                                    .matches("/" + url.getName() + "-service/v3/api-docs");
                        });

        // Verify api-gateway has its own URL pattern
        swaggerUiConfigProperties.getUrls().stream()
                .filter(url -> url.getName().equals("api-gateway"))
                .forEach(
                        url -> {
                            assertThat(url.getUrl()).isEqualTo("/v3/api-docs");
                        });
    }

    @Test
    void shouldCreateGroupedOpenApis() {
        assertThat(groupedOpenApis).isNotNull();
        assertThat(groupedOpenApis).isNotEmpty();
        assertThat(groupedOpenApis)
                .extracting("group")
                .containsExactlyInAnyOrder("order", "inventory", "catalog", "payment");
    }

    @Test
    void shouldCreateGroupedOpenApisWithCorrectDisplayNames() {
        assertThat(groupedOpenApis).isNotNull();
        assertThat(groupedOpenApis).isNotEmpty();
        assertThat(groupedOpenApis)
                .extracting("displayName")
                .containsExactlyInAnyOrder(
                        "ORDER Service", "INVENTORY Service", "CATALOG Service", "PAYMENT Service");
    }

    @Test
    void shouldCreateGroupedOpenApisWithCorrectPathsToMatch() {
        assertThat(groupedOpenApis).isNotNull();
        assertThat(groupedOpenApis).isNotEmpty();

        // Verify that each grouped API has the correct pathsToMatch pattern
        groupedOpenApis.forEach(
                api -> {
                    String group = api.getGroup();
                    assertThat(api.getPathsToMatch()).contains("/" + group + "/**");
                });
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
                            // Either it's a service name extracted from route (without -service
                            // suffix)
                            // or it's the api-gateway itself configured separately
                            String groupName = (String) group;
                            return groupName.equals("api-gateway")
                                    || List.of("order", "inventory", "catalog", "payment")
                                            .contains(groupName);
                        });
    }
}
