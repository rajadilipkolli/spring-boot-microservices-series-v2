package com.example.retailstore.webapp.web.controller;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Client;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;

import com.example.common.dto.PagedResult;
import com.example.retailstore.webapp.clients.catalog.ProductRequest;
import com.example.retailstore.webapp.clients.catalog.ProductResponse;
import com.example.retailstore.webapp.clients.customer.CustomerResponse;
import com.example.retailstore.webapp.clients.inventory.InventoryResponse;
import com.example.retailstore.webapp.clients.order.OrderResponse;
import com.example.retailstore.webapp.common.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.OAuth2AccessToken;

class DownstreamTokenPropagationIT extends AbstractIntegrationTest {

    private static final String REGISTRATION_ID = "retailstore-webapp";

    private ClientRegistration clientRegistration() {
        return clientRegistrationRepository.findByRegistrationId(REGISTRATION_ID);
    }

    private OAuth2AccessToken knownToken(String tokenValue) {
        return new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                tokenValue,
                Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(3600),
                Set.of("openid", "profile", "roles"));
    }

    @Test
    void shouldPropagateBearerTokenForOrderAndCustomerRequests() {
        String tokenValue = "order-user-access-token-12345";
        OAuth2AccessToken token = knownToken(tokenValue);
        ClientRegistration registration = clientRegistration();

        CustomerResponse customerResponse = new CustomerResponse(
                10L,
                "Order User",
                "orderuser@example.com",
                "1234567890",
                "Test Address",
                null,
                "Test City",
                "Test State",
                "12345",
                "Test Country",
                BigDecimal.ZERO);

        PagedResult<OrderResponse> pagedOrders =
                new PagedResult<>(Collections.emptyList(), 0L, 1, 0, true, true, false, false);

        gatewayServiceMock.stubFor(
                get(urlEqualTo("/payment-service/api/customers/by-email?email=orderuser%40example.com"))
                        .willReturn(aResponse()
                                .withStatus(HttpStatus.OK.value())
                                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                                .withBody(jsonMapper.writeValueAsString(customerResponse))));

        gatewayServiceMock.stubFor(get(urlEqualTo("/order-service/api/orders/customer/10"))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody(jsonMapper.writeValueAsString(pagedOrders))));

        mockMvcTester
                .get()
                .uri("/api/orders")
                .with(oidcLogin()
                        .clientRegistration(registration)
                        .idToken(idToken -> idToken.claim("sub", "order-user-sub")
                                .claim("preferred_username", "orderuser")
                                .claim("email", "orderuser@example.com")))
                .with(oauth2Client(REGISTRATION_ID)
                        .clientRegistration(registration)
                        .principalName("order-user-sub")
                        .accessToken(token))
                .assertThat()
                .hasStatus(HttpStatus.OK);

        gatewayServiceMock.verify(
                1,
                getRequestedFor(urlEqualTo("/payment-service/api/customers/by-email?email=orderuser%40example.com"))
                        .withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer " + tokenValue)));

        gatewayServiceMock.verify(
                1,
                getRequestedFor(urlEqualTo("/order-service/api/orders/customer/10"))
                        .withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer " + tokenValue)));
    }

    @Test
    void shouldPropagateBearerTokenForAdminInventoryRequest() {
        String tokenValue = "admin-inventory-token-67890";
        OAuth2AccessToken token = knownToken(tokenValue);
        ClientRegistration registration = clientRegistration();

        InventoryResponse inventoryItem = new InventoryResponse(1L, "PROD-1", 10, 0);
        PagedResult<InventoryResponse> pagedInventory =
                new PagedResult<>(List.of(inventoryItem), 1L, 0, 1, true, true, false, false);

        gatewayServiceMock.stubFor(get(urlEqualTo("/inventory-service/api/inventory?pageNo=0"))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody(jsonMapper.writeValueAsString(pagedInventory))));

        mockMvcTester
                .get()
                .uri("/api/inventory?page=0")
                .with(oidcLogin()
                        .clientRegistration(registration)
                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))
                        .idToken(idToken -> idToken.claim("sub", "admin-sub")
                                .claim("preferred_username", "adminuser")
                                .claim("email", "admin@example.com")))
                .with(oauth2Client(REGISTRATION_ID)
                        .clientRegistration(registration)
                        .principalName("admin-sub")
                        .accessToken(token))
                .assertThat()
                .hasStatus(HttpStatus.OK);

        gatewayServiceMock.verify(
                1,
                getRequestedFor(urlEqualTo("/inventory-service/api/inventory?pageNo=0"))
                        .withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer " + tokenValue)));
    }

    @Test
    void shouldPropagateBearerTokenForAdminProductCreateRequest() {
        String tokenValue = "admin-catalog-token-11223";
        OAuth2AccessToken token = knownToken(tokenValue);
        ClientRegistration registration = clientRegistration();

        ProductRequest productRequest =
                new ProductRequest("PROD-NEW", "Product Name", "Product Description", null, BigDecimal.valueOf(29.99));
        ProductResponse productResponse = new ProductResponse(
                50L, "PROD-NEW", "Product Name", "Product Description", null, BigDecimal.valueOf(29.99), true);

        gatewayServiceMock.stubFor(post(urlEqualTo("/catalog-service/api/catalog"))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody(jsonMapper.writeValueAsString(productResponse))));

        mockMvcTester
                .post()
                .uri("/api/products")
                .with(csrf())
                .with(oidcLogin()
                        .clientRegistration(registration)
                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))
                        .idToken(idToken -> idToken.claim("sub", "admin-catalog-sub")
                                .claim("preferred_username", "admincatalog")
                                .claim("email", "admincatalog@example.com")))
                .with(oauth2Client(REGISTRATION_ID)
                        .clientRegistration(registration)
                        .principalName("admin-catalog-sub")
                        .accessToken(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(productRequest))
                .assertThat()
                .hasStatus(HttpStatus.OK);

        gatewayServiceMock.verify(
                1,
                postRequestedFor(urlEqualTo("/catalog-service/api/catalog"))
                        .withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer " + tokenValue)));
    }

    @Test
    void shouldNotSendAuthorizationHeaderForAnonymousPublicProductListing() {
        gatewayServiceMock.stubFor(get(urlEqualTo("/catalog-service/api/catalog?pageNo=0"))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("""
                            {
                              "data": [],
                              "totalElements": 0,
                              "pageNumber": 1,
                              "totalPages": 0,
                              "isFirst": true,
                              "isLast": true,
                              "hasNext": false,
                              "hasPrevious": false
                            }
                        """)));

        mockMvcTester.get().uri("/api/products").assertThat().hasStatus(HttpStatus.OK);

        gatewayServiceMock.verify(
                1,
                getRequestedFor(urlEqualTo("/catalog-service/api/catalog?pageNo=0"))
                        .withHeader(HttpHeaders.AUTHORIZATION, absent()));
    }
}
