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
    void shouldCreateGroupedOpenApis() {
        assertThat(groupedOpenApis).isNotNull();
        assertThat(groupedOpenApis).isNotEmpty();
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
}
