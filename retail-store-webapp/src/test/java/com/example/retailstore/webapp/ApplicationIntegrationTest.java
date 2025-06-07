package com.example.retailstore.webapp;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.retailstore.webapp.clients.PagedResult;
import com.example.retailstore.webapp.common.AbstractIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

class ApplicationIntegrationTest extends AbstractIntegrationTest {

    @Test
    void shouldReturnHealthyStatusFromActuatorEndpoint() {
        mockMvcTester
                .get()
                .uri("/actuator/health")
                .assertThat()
                .hasContentType("application/vnd.spring-boot.actuator.v3+json")
                .hasHeader("Cache-Control", "no-cache, no-store, max-age=0, must-revalidate")
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("UP");
    }

    @Test
    void shouldFetchAvailableProducts() {
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
                    assertThat(productMap.get("imageUrl")).isNull(); // Not in stub, so defaults to null for String
                    // For numeric types like double, if not present in JSON, they might default to 0.0 or cause an
                    // error
                    // depending on Jackson's deserialization configuration. Assuming 0.0 for price.
                    assertThat(productMap.get("price")).isEqualTo(0.0);
                    // For boolean types, if not present, they default to false.
                    assertThat(productMap.get("inStock")).isEqualTo(true);
                });
    }
}
