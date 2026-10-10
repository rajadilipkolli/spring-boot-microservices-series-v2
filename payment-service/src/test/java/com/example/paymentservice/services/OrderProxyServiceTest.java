/*** Licensed under MIT License Copyright (c) 2026 Raja Kolli. ***/
package com.example.paymentservice.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.paymentservice.model.response.OrderResponse;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import tools.jackson.databind.json.JsonMapper;

class OrderProxyServiceTest {

    private MockRestServiceServer server;
    private OrderProxyService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://order-service");
        server = MockRestServiceServer.bindTo(builder).build();
        OrderServiceProxy proxy =
                HttpServiceProxyFactory.builderFor(RestClientAdapter.create(builder.build()))
                        .build()
                        .createClient(OrderServiceProxy.class);
        service = new OrderProxyService(proxy);
    }

    @ParameterizedTest
    @CsvSource({"1,10,id,asc,0", "3,5,createdDate,desc,2"})
    void shouldSendPageableParametersAndPreserveOrderDetails(
            int pageNo, int pageSize, String sortBy, String sortDir, int expectedPage) {
        server.expect(queryParam("page", Integer.toString(expectedPage)))
                .andExpect(queryParam("size", Integer.toString(pageSize)))
                .andExpect(queryParam("sort", sortBy + "%2C" + sortDir))
                .andRespond(
                        withSuccess(
                                """
                        {
                          "data": [{
                            "orderId": "9007199254740993", "customerId": "9007199254740995",
                            "status": "COMPLETED", "source": "PAYMENT",
                            "createdDate": "2026-10-10T12:00:00", "totalPrice": 20.00,
                            "deliveryAddress": {
                              "addressLine1": "Street", "addressLine2": "Suite",
                              "city": "City", "state": "State", "zipCode": "Zip", "country": "Country"
                            },
                            "items": [{"itemId": "9007199254740997", "productId": "P1",
                              "quantity": 2, "productPrice": 10.00, "price": 20.00}]
                          }],
                          "totalElements": 1, "pageNumber": 1, "totalPages": 1,
                          "isFirst": true, "isLast": true, "hasNext": false, "hasPrevious": false
                        }
                        """,
                                MediaType.APPLICATION_JSON));

        var result =
                service.getOrdersByCustomerId(9007199254740995L, pageNo, pageSize, sortBy, sortDir);
        OrderResponse order = result.data().getFirst();
        assertThat(order.deliveryAddress())
                .isEqualTo(
                        new OrderResponse.Address(
                                "Street", "Suite", "City", "State", "Zip", "Country"));
        assertThat(order.items().getFirst().productPrice())
                .isEqualByComparingTo(new BigDecimal("10"));
        assertThat(order.items().getFirst().price()).isEqualByComparingTo(new BigDecimal("20"));
        var json = JsonMapper.builder().build().valueToTree(order);
        assertThat(json.get("orderId").asString()).isEqualTo("9007199254740993");
        assertThat(json.get("orderId").isString()).isTrue();
        assertThat(json.get("customerId").asString()).isEqualTo("9007199254740995");
        assertThat(json.get("customerId").isString()).isTrue();
        assertThat(json.get("items").get(0).get("itemId").asString()).isEqualTo("9007199254740997");
        assertThat(json.get("items").get(0).get("itemId").isString()).isTrue();
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 503})
    void shouldPropagateHttpFailure(int status) {
        server.expect(
                        requestTo(
                                "http://order-service/api/orders/customer/1?page=0&size=10&sort=id%2Casc"))
                .andRespond(withStatus(HttpStatus.valueOf(status)));

        assertThatThrownBy(() -> service.getOrdersByCustomerId(1L, 1, 10, "id", "asc"))
                .isInstanceOfSatisfying(
                        RestClientResponseException.class,
                        exception ->
                                assertThat(exception.getStatusCode().value()).isEqualTo(status));
        server.verify();
    }
}
