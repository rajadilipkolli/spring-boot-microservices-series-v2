package com.example.retailstore.webapp.web.controller;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.example.retailstore.webapp.clients.PagedResult;
import com.example.retailstore.webapp.clients.catalog.ProductRequest;
import com.example.retailstore.webapp.clients.catalog.ProductResponse;
import com.example.retailstore.webapp.common.AbstractIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

class ProductControllerIT extends AbstractIntegrationTest {

    @Test
    void testProducts() {
        // Example stub for catalog service
        gatewayServiceMock.stubFor(
                get(urlEqualTo("/catalog-service/api/catalog?pageNo=0"))
                        .willReturn(
                                aResponse()
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(
                                                """
                            {
                              "data": [
                                {
                                  "id": 1,
                                  "productCode": "TESTPROD001",
                                  "productName": "Test Product",
                                  "description": "A beautiful product",
                                  "inStock": true
                                }
                              ],
                              "totalElements": 1,
                              "pageNumber": 1,
                              "totalPages": 1,
                              "isFirst": true,
                              "isLast": true,
                              "hasNext": false,
                              "hasPrevious": false
                            }
                        """)));

        mockMvcTester
                .get()
                .uri("/api/products")
                .assertThat()
                .hasContentType(MediaType.APPLICATION_JSON)
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .convertTo(PagedResult.class)
                .satisfies(pagedResult -> {
                    assertThat(pagedResult).isNotNull();

                    // Assertions for PagedResult fields
                    assertThat(pagedResult.totalElements()).isEqualTo(1L);
                    assertThat(pagedResult.pageNumber()).isEqualTo(1);
                    assertThat(pagedResult.totalPages()).isEqualTo(1);
                    assertThat(pagedResult.isFirst()).isTrue();
                    assertThat(pagedResult.isLast()).isTrue();
                    assertThat(pagedResult.hasNext()).isFalse();
                    assertThat(pagedResult.hasPrevious()).isFalse();

                    // Assertions for the data content
                    assertThat(pagedResult.data()).isNotEmpty().hasSize(1);

                    // Due to type erasure with PagedResult.class, pagedResult.data() will be List<Object>,
                    // where each Object is a LinkedHashMap.
                    Object rawProductData = pagedResult.data().getFirst();
                    assertThat(rawProductData).isInstanceOf(Map.class);
                    @SuppressWarnings("unchecked") // Safe cast after isInstanceOf check
                    Map<String, Object> productMap = (Map<String, Object>) rawProductData;

                    assertThat(productMap.get("id")).isEqualTo(1); // JSON '1' becomes Integer 1
                    assertThat(productMap.get("productCode")).isEqualTo("TESTPROD001");
                    assertThat(productMap.get("productName")).isEqualTo("Test Product");
                    assertThat(productMap.get("description")).isEqualTo("A beautiful product");
                    assertThat(productMap.get("imageUrl")).isNull();
                    assertThat(productMap.get("price")).isEqualTo(0.0);
                    assertThat(productMap.get("inStock")).isEqualTo(true);
                });
    }

    @Test
    void testCreateProduct() {
        // Example stub for catalog service
        gatewayServiceMock.stubFor(
                post(urlEqualTo("/catalog-service/api/catalog"))
                        .willReturn(
                                aResponse()
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(
                                                """
                            {
                              "id": 100,
                              "productCode": "NEWPROD001",
                              "productName": "New Product",
                              "description": "A newly created product",
                              "price": 19.99,
                              "inStock": true
                            }
                        """)));

        ProductRequest productRequest =
                new ProductRequest("NEWPROD001", "New Product", "A newly created product", null, 19.99);

        mockMvcTester
                .post()
                .uri("/api/products")
                .with(csrf())
                .with(user("admin").roles("ADMIN")) // Authenticate as admin
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(productRequest)) // Serialize request body to JSON
                .assertThat()
                .hasStatus(HttpStatus.OK)
                .hasContentType(MediaType.APPLICATION_JSON)
                .bodyJson()
                .convertTo(ProductResponse.class)
                .satisfies(response -> {
                    assertThat(response).isNotNull();
                    assertThat(response.id()).isEqualTo(100L);
                    assertThat(response.productCode()).isEqualTo("NEWPROD001");
                    assertThat(response.productName()).isEqualTo("New Product");
                    assertThat(response.description()).isEqualTo("A newly created product");
                    assertThat(response.price()).isEqualTo(19.99);
                    assertThat(response.inStock()).isTrue();
                });
    }
}
