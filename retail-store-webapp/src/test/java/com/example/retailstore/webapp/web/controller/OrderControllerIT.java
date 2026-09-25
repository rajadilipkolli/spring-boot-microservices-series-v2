package com.example.retailstore.webapp.web.controller;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;

import com.example.retailstore.webapp.clients.PagedResult;
import com.example.retailstore.webapp.clients.customer.CustomerRequest;
import com.example.retailstore.webapp.clients.customer.CustomerResponse;
import com.example.retailstore.webapp.clients.order.Address;
import com.example.retailstore.webapp.clients.order.CreateOrderRequest;
import com.example.retailstore.webapp.clients.order.OrderConfirmationDTO;
import com.example.retailstore.webapp.clients.order.OrderItemRequest;
import com.example.retailstore.webapp.clients.order.OrderResponse;
import com.example.retailstore.webapp.common.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

class OrderControllerIT extends AbstractIntegrationTest {

    @Test
    void testCreateOrder() {
        CustomerRequest customerRequest =
                new CustomerRequest("Test User", "test@example.com", "1234567890", "Test Address", 0);
        Address address = new Address("Line1", "Line2", "City", "State", "Zip", "Country");
        OrderItemRequest orderItemRequest = new OrderItemRequest("PROD001", 1, BigDecimal.TEN);
        CreateOrderRequest createOrderRequest =
                new CreateOrderRequest(Collections.singletonList(orderItemRequest), customerRequest, address);

        CustomerResponse customerResponse =
                new CustomerResponse(1L, "Test User", "test@example.com", "1234567890", "Test Address", 0);
        OrderConfirmationDTO orderConfirmationDTO = new OrderConfirmationDTO(1L, 1L, "CONFIRMED");

        // Mock Customer Service
        gatewayServiceMock.stubFor(get(urlEqualTo("/payment-service/api/customers/by-email?email=test%40example.com"))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody(jsonMapper.writeValueAsString(customerResponse))));

        // Mock Order Service
        gatewayServiceMock.stubFor(post(urlEqualTo("/order-service/api/orders"))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.CREATED.value())
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody(jsonMapper.writeValueAsString(orderConfirmationDTO))));

        mockMvcTester
                .post()
                .uri("/api/orders")
                .content(jsonMapper.writeValueAsString(createOrderRequest))
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf())
                .with(oidcLogin().idToken(token -> token.claim("email", "test@example.com")))
                .assertThat()
                .hasStatus(HttpStatus.OK)
                .hasContentType(MediaType.APPLICATION_JSON)
                .bodyJson()
                .convertTo(OrderConfirmationDTO.class)
                .usingRecursiveComparison()
                .isEqualTo(orderConfirmationDTO);
    }

    @Test
    void testGetOrder() {
        OrderResponse orderResponse = new OrderResponse();
        orderResponse.setOrderId(1L);
        orderResponse.setCustomerId(2L);
        CustomerResponse customerResponse =
                new CustomerResponse(2L, "Test User", "testemail@gmail.com", "1234567890", "Test Address", 0);
        orderResponse.setCustomer(customerResponse); // Set the customer in the expected response
        gatewayServiceMock.stubFor(
                get(urlEqualTo("/payment-service/api/customers/by-email?email=testemail%40gmail.com"))
                        .willReturn(aResponse()
                                .withStatus(HttpStatus.OK.value())
                                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                                .withBody(jsonMapper.writeValueAsString(customerResponse))));

        gatewayServiceMock.stubFor(get(urlEqualTo("/order-service/api/orders/1"))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody(jsonMapper.writeValueAsString(orderResponse))));

        mockMvcTester
                .get()
                .uri("/api/orders/1")
                .with(oidcLogin().idToken(token -> token.claim("email", "testemail@gmail.com")))
                .assertThat()
                .hasStatus(HttpStatus.OK)
                .hasContentType(MediaType.APPLICATION_JSON)
                .bodyJson()
                .convertTo(OrderResponse.class)
                .usingRecursiveComparison()
                .isEqualTo(orderResponse);
    }

    @Test
    void testGetOrderWithLiteralJsonFromOrderService() {
        String literalJsonResponse = """
                {
                  "orderId": 102,
                  "customerId": 405,
                  "status": "CONFIRMED",
                  "source": null,
                  "deliveryAddress": {
                    "addressLine1": "Apt. 668 05736 Feeney Brooks",
                    "addressLine2": "Port Salvatoreside",
                    "city": "WI 14512",
                    "state": "TS",
                    "zipCode": "500072",
                    "country": "India"
                  },
                  "createdDate": "2026-08-09T11:01:00.157888",
                  "totalPrice": 3299.97,
                  "items": [
                    {
                      "itemId": 202,
                      "productId": "P003",
                      "quantity": 1,
                      "productPrice": 1299.99,
                      "price": 1299.99
                    },
                    {
                      "itemId": 203,
                      "productId": "P002",
                      "quantity": 1,
                      "productPrice": 899.99,
                      "price": 899.99
                    }
                  ]
                }
                """;

        CustomerResponse customerResponse =
                new CustomerResponse(405L, "Test User", "testemail@gmail.com", "1234567890", "Test Address", 0);

        gatewayServiceMock.stubFor(
                get(urlEqualTo("/payment-service/api/customers/by-email?email=testemail%40gmail.com"))
                        .willReturn(aResponse()
                                .withStatus(HttpStatus.OK.value())
                                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                                .withBody(jsonMapper.writeValueAsString(customerResponse))));

        gatewayServiceMock.stubFor(get(urlEqualTo("/order-service/api/orders/102"))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody(literalJsonResponse)));

        mockMvcTester
                .get()
                .uri("/api/orders/102")
                .with(oidcLogin().idToken(token -> token.claim("email", "testemail@gmail.com")))
                .assertThat()
                .hasStatus(HttpStatus.OK)
                .hasContentType(MediaType.APPLICATION_JSON)
                .bodyJson()
                .convertTo(OrderResponse.class)
                .satisfies(res -> {
                    assertThat(res.getOrderId()).isEqualTo(102L);
                    assertThat(res.getCustomerId()).isEqualTo(405L);
                    assertThat(res.getTotalPrice()).isEqualByComparingTo(new BigDecimal("3299.97"));
                    assertThat(res.getStatus()).isEqualTo("CONFIRMED");
                    assertThat(res.getCreatedDate())
                            .isEqualTo(java.time.LocalDateTime.parse("2026-08-09T11:01:00.157888"));
                    assertThat(res.getDeliveryAddress().addressLine1()).isEqualTo("Apt. 668 05736 Feeney Brooks");
                    assertThat(res.getDeliveryAddress().addressLine2()).isEqualTo("Port Salvatoreside");
                    assertThat(res.getDeliveryAddress().city()).isEqualTo("WI 14512");
                    assertThat(res.getDeliveryAddress().state()).isEqualTo("TS");
                    assertThat(res.getDeliveryAddress().zipCode()).isEqualTo("500072");
                    assertThat(res.getDeliveryAddress().country()).isEqualTo("India");
                    assertThat(res.getItems()).hasSize(2);
                    assertThat(res.getItems().get(0).itemId()).isEqualTo(202L);
                    assertThat(res.getItems().get(0).price()).isEqualByComparingTo(new BigDecimal("1299.99"));
                    assertThat(res.getItems().get(1).itemId()).isEqualTo(203L);
                    assertThat(res.getItems().get(1).price()).isEqualByComparingTo(new BigDecimal("899.99"));
                });
    }

    @Test
    void testGetOrders() {
        PagedResult<OrderResponse> pagedResult =
                new PagedResult<>(Collections.emptyList(), 0L, 1, 0, true, true, false, false);

        CustomerResponse customerResponse =
                new CustomerResponse(4L, "Empty User", "empty@gmail.com", "1234567890", "Test Address", 0);

        gatewayServiceMock.stubFor(get(urlEqualTo("/payment-service/api/customers/by-email?email=empty%40gmail.com"))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody(jsonMapper.writeValueAsString(customerResponse))));

        gatewayServiceMock.stubFor(get(urlEqualTo("/order-service/api/orders/customer/4"))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody(jsonMapper.writeValueAsString(pagedResult))));

        mockMvcTester
                .get()
                .uri("/api/orders")
                .with(oidcLogin().idToken(token -> token.claim("email", "empty@gmail.com")))
                .assertThat()
                .hasStatus(HttpStatus.OK)
                .hasContentType(MediaType.APPLICATION_JSON)
                .bodyJson()
                .convertTo(PagedResult.class)
                .satisfies(response -> {
                    assertThat(response.data()).isEmpty();
                    assertThat(response.totalElements()).isZero();
                    assertThat(response.pageNumber()).isEqualTo(1);
                    assertThat(response.totalPages()).isZero();
                    assertThat(response.isFirst()).isTrue();
                    assertThat(response.isLast()).isTrue();
                    assertThat(response.hasNext()).isFalse();
                    assertThat(response.hasPrevious()).isFalse();
                });
    }

    @Test
    void testGetOrder_Forbidden() {
        OrderResponse orderResponse = new OrderResponse();
        orderResponse.setOrderId(1L);
        orderResponse.setCustomerId(2L);
        CustomerResponse orderCustomer =
                new CustomerResponse(2L, "Test User", "testemail@gmail.com", "1234567890", "Test Address", 0);
        orderResponse.setCustomer(orderCustomer);

        CustomerResponse loggedInCustomer =
                new CustomerResponse(3L, "Other User", "other@gmail.com", "1234567890", "Test Address", 0);

        gatewayServiceMock.stubFor(get(urlEqualTo("/payment-service/api/customers/by-email?email=other%40gmail.com"))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody(jsonMapper.writeValueAsString(loggedInCustomer))));

        gatewayServiceMock.stubFor(get(urlEqualTo("/order-service/api/orders/1"))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody(jsonMapper.writeValueAsString(orderResponse))));

        mockMvcTester
                .get()
                .uri("/api/orders/1")
                .with(oidcLogin().idToken(token -> token.claim("email", "other@gmail.com")))
                .assertThat()
                .hasStatus(HttpStatus.NOT_FOUND);
    }
}
