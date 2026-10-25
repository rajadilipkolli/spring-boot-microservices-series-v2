/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.catalogservice.config;

import com.example.catalogservice.common.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class SwaggerConfigIntegrationTest extends AbstractIntegrationTest {

    @Test
    void shouldServeOpenAPIEndpoint() {
        webTestClient
                .get()
                .uri("/v3/api-docs")
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.info.title")
                .isEqualTo("catalog-service")
                .jsonPath("$.info.description")
                .isEqualTo("APIs related to Catalog")
                .jsonPath("$.info.version")
                .isEqualTo("v1")
                // Check 9 catalog operations
                .jsonPath("$.paths['/api/catalog'].get")
                .exists()
                .jsonPath("$.paths['/api/catalog'].post")
                .exists()
                .jsonPath("$.paths['/api/catalog/id/{id}'].get")
                .exists()
                .jsonPath("$.paths['/api/catalog/{id}'].put")
                .exists()
                .jsonPath("$.paths['/api/catalog/{id}'].delete")
                .exists()
                .jsonPath("$.paths['/api/catalog/product-code/{productCode}'].get")
                .exists()
                .jsonPath("$.paths['/api/catalog/exists'].get")
                .exists()
                .jsonPath("$.paths['/api/catalog/generate'].post")
                .exists()
                .jsonPath("$.paths['/api/catalog/search'].get")
                .exists()
                // Check paginated 200 schema resolves concrete element type ProductResponse
                .jsonPath("$.components.schemas.ProductResponse")
                .exists()
                .jsonPath(
                        "$.paths['/api/catalog'].get.responses['200'].content['application/json'].schema.$ref")
                .value(
                        ref ->
                                org.assertj.core.api.Assertions.assertThat((String) ref)
                                        .contains("ProductResponse"));
    }
}
