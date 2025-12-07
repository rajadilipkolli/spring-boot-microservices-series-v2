package com.example.retailstore.webapp.web.controller;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

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
    void testCreateOrder() throws Exception {
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
        gatewayServiceMock.stubFor(get(urlEqualTo("/payment-service/api/customers/name/Test%20User"))
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
                .with(user("user").roles("USER"))
                .assertThat()
                .hasStatus(HttpStatus.OK)
                .hasContentType(MediaType.APPLICATION_JSON)
                .bodyJson()
                .convertTo(OrderConfirmationDTO.class)
                .usingRecursiveComparison()
                .isEqualTo(orderConfirmationDTO);
    }

    @Test
    void testGetOrder() throws Exception {
        OrderResponse orderResponse = new OrderResponse();
        orderResponse.setOrderId(1L);
        orderResponse.setCustomerId(2L);
        CustomerResponse customerResponse =
                new CustomerResponse(2L, "Test User", "testEmail@gmail.com", "1234567890", "Test Address", 0);
        orderResponse.setCustomer(customerResponse); // Set the customer in the expected response
        gatewayServiceMock.stubFor(get(urlEqualTo("/payment-service/api/customers/2"))
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
                .with(user("user").roles("USER"))
                .assertThat()
                .hasStatus(HttpStatus.OK)
                .hasContentType(MediaType.APPLICATION_JSON)
                .bodyJson()
                .convertTo(OrderResponse.class)
                .usingRecursiveComparison()
                .isEqualTo(orderResponse);
    }

    @Test
    void testGetOrders() throws Exception {
        PagedResult<OrderResponse> pagedResult =
                new PagedResult<>(Collections.emptyList(), 0L, 1, 0, true, true, false, false);

        gatewayServiceMock.stubFor(get(urlEqualTo("/order-service/api/orders")) // Removed query params from mock URL
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody(jsonMapper.writeValueAsString(pagedResult))));

        mockMvcTester
                .get()
                .uri("/api/orders")
                .with(user("user").roles("USER"))
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
}
