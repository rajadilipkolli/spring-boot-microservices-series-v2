/***
<p>
    Licensed under MIT License Copyright (c) 2021-2025 Raja Kolli.
</p>
***/

package com.example.orderservice.web.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.orderservice.entities.Order;
import com.example.orderservice.entities.OrderStatus;
import com.example.orderservice.model.Address;
import com.example.orderservice.model.request.OrderItemRequest;
import com.example.orderservice.model.request.OrderRequest;
import com.example.orderservice.model.response.OrderItemResponse;
import com.example.orderservice.model.response.OrderResponse;
import com.example.orderservice.model.response.PagedResult;
import com.example.orderservice.services.OrderGeneratorService;
import com.example.orderservice.services.OrderKafkaStreamService;
import com.example.orderservice.services.OrderService;
import com.example.orderservice.utils.AppConstants;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

@WebMvcTest(controllers = OrderController.class)
@ActiveProfiles(AppConstants.PROFILE_TEST)
class OrderControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private OrderService orderService;

    @MockitoBean private OrderGeneratorService orderGeneratorService;

    @MockitoBean private OrderKafkaStreamService orderKafkaStreamService;

    @Autowired private JsonMapper jsonMapper;

    @Test
    void shouldFetchAllOrders() throws Exception {

        List<OrderResponse> orderResponseList = new ArrayList<>();
        orderResponseList.add(
                new OrderResponse(
                        null,
                        1L,
                        "NEW",
                        "",
                        new Address(
                                "Junit Address1",
                                "AddressLine2",
                                "city",
                                "state",
                                "zipCode",
                                "country"),
                        LocalDateTime.now(),
                        BigDecimal.TEN,
                        new ArrayList<>()));
        orderResponseList.add(
                new OrderResponse(
                        null,
                        1L,
                        "NEW",
                        "",
                        new Address(
                                "Junit Address1",
                                "AddressLine2",
                                "city",
                                "state",
                                "zipCode",
                                "country"),
                        LocalDateTime.now(),
                        BigDecimal.TEN,
                        new ArrayList<>()));
        orderResponseList.add(
                new OrderResponse(
                        null,
                        1L,
                        "NEW",
                        "",
                        new Address(
                                "Junit Address1",
                                "AddressLine2",
                                "city",
                                "state",
                                "zipCode",
                                "country"),
                        LocalDateTime.now(),
                        BigDecimal.TEN,
                        new ArrayList<>()));
        Page<OrderResponse> page = new PageImpl<>(orderResponseList);
        PagedResult<OrderResponse> orderResponsePagedResult = new PagedResult<>(page);

        given(orderService.findAllOrders(0, 10, "id", "asc")).willReturn(orderResponsePagedResult);

        this.mockMvc
                .perform(get("/api/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()", is(8)))
                .andExpect(jsonPath("$.data.size()", is(orderResponseList.size())))
                .andExpect(jsonPath("$.totalElements", is(3)))
                .andExpect(jsonPath("$.pageNumber", is(1)))
                .andExpect(jsonPath("$.totalPages", is(1)))
                .andExpect(jsonPath("$.isFirst", is(true)))
                .andExpect(jsonPath("$.isLast", is(true)))
                .andExpect(jsonPath("$.hasNext", is(false)))
                .andExpect(jsonPath("$.hasPrevious", is(false)))
                // Enhanced assertions for data structure verification
                .andExpect(jsonPath("$.data[0].customerId", is(1)))
                .andExpect(jsonPath("$.data[0].status", is("NEW")))
                .andExpect(jsonPath("$.data[0].source", is("")))
                .andExpect(jsonPath("$.data[0].deliveryAddress", notNullValue()))
                .andExpect(jsonPath("$.data[0].deliveryAddress.addressLine1", is("Junit Address1")))
                .andExpect(jsonPath("$.data[0].deliveryAddress.city", is("city")))
                .andExpect(jsonPath("$.data[0].createdDate", notNullValue()))
                .andExpect(jsonPath("$.data[0].totalPrice").value(10));
    }

    @Nested
    @DisplayName("find methods")
    class Find {

        @Test
        void shouldFindOrderById() throws Exception {
            Long orderId = 1L;
            LocalDateTime testDateTime = LocalDateTime.now();
            OrderResponse orderResponse =
                    new OrderResponse(
                            1L,
                            1L,
                            "NEW",
                            "",
                            getDeliveryAddress(),
                            testDateTime,
                            BigDecimal.TEN,
                            new ArrayList<>());
            given(orderService.findOrderByIdAsResponse(orderId))
                    .willReturn(Optional.of(orderResponse));

            mockMvc.perform(get("/api/orders/{id}", orderId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.orderId", is(orderResponse.orderId()), Long.class))
                    .andExpect(jsonPath("$.customerId", is(orderResponse.customerId()), Long.class))
                    .andExpect(jsonPath("$.status", is(orderResponse.status())))
                    .andExpect(jsonPath("$.source", is(orderResponse.source())))
                    .andExpect(jsonPath("$.deliveryAddress", notNullValue()))
                    .andExpect(jsonPath("$.deliveryAddress.addressLine1", is("Junit Address1")))
                    .andExpect(jsonPath("$.deliveryAddress.addressLine2", is("AddressLine2")))
                    .andExpect(jsonPath("$.deliveryAddress.city", is("city")))
                    .andExpect(jsonPath("$.deliveryAddress.state", is("state")))
                    .andExpect(jsonPath("$.deliveryAddress.zipCode", is("zipCode")))
                    .andExpect(jsonPath("$.deliveryAddress.country", is("country")))
                    .andExpect(jsonPath("$.createdDate", notNullValue()))
                    .andExpect(jsonPath("$.totalPrice").value(is(10)))
                    .andExpect(jsonPath("$.items", notNullValue()))
                    .andExpect(jsonPath("$.items", hasSize(0)));
        }

        private Address getDeliveryAddress() {
            return new Address(
                    "Junit Address1", "AddressLine2", "city", "state", "zipCode", "country");
        }

        @Test
        void shouldRespectDelayParameter() throws Exception {
            Long orderId = 1L;
            LocalDateTime testDateTime = LocalDateTime.now();
            OrderResponse orderResponse =
                    new OrderResponse(
                            1L,
                            1L,
                            "NEW",
                            "",
                            getDeliveryAddress(),
                            testDateTime,
                            BigDecimal.TEN,
                            new ArrayList<>());
            given(orderService.findOrderByIdAsResponse(orderId))
                    .willReturn(Optional.of(orderResponse));

            long startTime = System.nanoTime();
            mockMvc.perform(get("/api/orders/{id}", orderId).param("delay", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.orderId", is(1)));
            long duration = (System.nanoTime() - startTime) / 1_000_000; // Convert to milliseconds

            assertThat(duration).isGreaterThanOrEqualTo(900);
            verify(orderService).findOrderByIdAsResponse(orderId);
        }

        @Test
        void shouldReturn404WhenFetchingNonExistingOrder() throws Exception {
            Long orderId = 1L;
            given(orderService.findOrderByIdAsResponse(orderId)).willReturn(Optional.empty());

            mockMvc.perform(get("/api/orders/{id}", orderId))
                    .andExpect(status().isNotFound())
                    .andExpect(
                            header().string(
                                            "Content-Type",
                                            is(MediaType.APPLICATION_PROBLEM_JSON_VALUE)))
                    .andExpect(
                            jsonPath(
                                    "$.type", is("https://api.microservices.com/errors/not-found")))
                    .andExpect(jsonPath("$.title", is("Order Not Found")))
                    .andExpect(jsonPath("$.status", is(404)))
                    .andExpect(
                            jsonPath("$.detail")
                                    .value("Order with Id %d not found".formatted(orderId)));
        }
    }

    @Nested
    @DisplayName("save methods")
    class Save {
        @Test
        void shouldCreateNewOrder() throws Exception {

            OrderRequest orderRequest =
                    new OrderRequest(
                            1L,
                            List.of(new OrderItemRequest("Product1", 10, BigDecimal.TEN)),
                            new Address(
                                    "Junit Address1",
                                    "AddressLine2",
                                    "city",
                                    "state",
                                    "zipCode",
                                    "country"));
            OrderItemResponse orderItemDto =
                    new OrderItemResponse(2L, "Product1", 10, BigDecimal.TEN, new BigDecimal(100));
            OrderResponse orderResponse =
                    new OrderResponse(
                            1L,
                            1L,
                            "NEW",
                            "",
                            new Address(
                                    "Junit Address1",
                                    "AddressLine2",
                                    "city",
                                    "state",
                                    "zipCode",
                                    "country"),
                            LocalDateTime.now(),
                            BigDecimal.TEN,
                            List.of(orderItemDto));
            given(orderService.saveOrder(any(OrderRequest.class))).willReturn(orderResponse);

            mockMvc.perform(
                            post("/api/orders")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(jsonMapper.writeValueAsString(orderRequest)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.orderId", notNullValue()))
                    .andExpect(jsonPath("$.customerId", is(orderResponse.customerId()), Long.class))
                    .andExpect(jsonPath("$.status", is("NEW")))
                    .andExpect(jsonPath("$.source", is("")))
                    .andExpect(jsonPath("$.createdDate", notNullValue()))
                    .andExpect(jsonPath("$.totalPrice").value(is(10)))
                    .andExpect(jsonPath("$.items.size()", is(1)))
                    .andExpect(jsonPath("$.items[0].itemId", is(2)))
                    .andExpect(jsonPath("$.items[0].productId", is("Product1")))
                    .andExpect(jsonPath("$.items[0].quantity", is(10)))
                    .andExpect(jsonPath("$.items[0].price").value(is(100)))
                    .andExpect(jsonPath("$.items[0].productPrice").value(is(10)))
                    .andExpect(jsonPath("$.deliveryAddress.addressLine1", is("Junit Address1")))
                    .andExpect(jsonPath("$.deliveryAddress.addressLine2", is("AddressLine2")))
                    .andExpect(jsonPath("$.deliveryAddress.city", is("city")))
                    .andExpect(jsonPath("$.deliveryAddress.state", is("state")))
                    .andExpect(jsonPath("$.deliveryAddress.zipCode", is("zipCode")))
                    .andExpect(jsonPath("$.deliveryAddress.country", is("country")));
        }

        @Test
        void shouldFailCreatingNewOrder() throws Exception {

            OrderRequest orderRequest =
                    new OrderRequest(
                            1L,
                            new ArrayList<>(),
                            new Address(
                                    "Junit Address1",
                                    "AddressLine2",
                                    "city",
                                    "state",
                                    "zipCode",
                                    "country"));

            mockMvc.perform(
                            post("/api/orders")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(jsonMapper.writeValueAsString(orderRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(
                            header().string(
                                            HttpHeaders.CONTENT_TYPE,
                                            is(MediaType.APPLICATION_PROBLEM_JSON_VALUE)))
                    .andExpect(
                            jsonPath(
                                    "$.type",
                                    is("https://api.microservices.com/errors/validation-error")))
                    .andExpect(jsonPath("$.title", is("Constraint Violation")))
                    .andExpect(jsonPath("$.status", is(400)))
                    .andExpect(jsonPath("$.detail", is("Invalid request content.")))
                    .andExpect(jsonPath("$.instance", is("/api/orders")))
                    .andExpect(jsonPath("$.violations", hasSize(1)))
                    .andExpect(jsonPath("$.violations[0].field", is("items")))
                    .andExpect(
                            jsonPath(
                                    "$.violations[0].message", is("Order without items not valid")))
                    .andReturn();
        }

        @Test
        void shouldReturn400WhenCreateNewOrderWithoutCustomerId() throws Exception {
            OrderRequest orderRequest =
                    new OrderRequest(
                            0L,
                            List.of(new OrderItemRequest("P001", 10, BigDecimal.TEN)),
                            new Address(
                                    "Junit Address1",
                                    "AddressLine2",
                                    "city",
                                    "state",
                                    "zipCode",
                                    "country"));

            mockMvc.perform(
                            post("/api/orders")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(jsonMapper.writeValueAsString(orderRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(
                            header().string(
                                            HttpHeaders.CONTENT_TYPE,
                                            is(MediaType.APPLICATION_PROBLEM_JSON_VALUE)))
                    .andExpect(
                            jsonPath(
                                    "$.type",
                                    is("https://api.microservices.com/errors/validation-error")))
                    .andExpect(jsonPath("$.title", is("Constraint Violation")))
                    .andExpect(jsonPath("$.status", is(400)))
                    .andExpect(jsonPath("$.detail", is("Invalid request content.")))
                    .andExpect(jsonPath("$.instance", is("/api/orders")))
                    .andExpect(jsonPath("$.violations", hasSize(1)))
                    .andExpect(jsonPath("$.violations[0].field", is("customerId")))
                    .andExpect(
                            jsonPath(
                                    "$.violations[0].message", is("CustomerId should be positive")))
                    .andReturn();
        }
    }

    @Nested
    @DisplayName("update methods")
    class Update {
        @Test
        void shouldUpdateOrder() throws Exception {

            OrderRequest orderRequest =
                    new OrderRequest(
                            1L,
                            List.of(new OrderItemRequest("Product1", 10, BigDecimal.TEN)),
                            new Address(
                                    "Junit Address1",
                                    "AddressLine2",
                                    "city",
                                    "state",
                                    "zipCode",
                                    "country"));

            LocalDateTime testDateTime = LocalDateTime.now();
            OrderResponse orderResponse =
                    new OrderResponse(
                            1L,
                            1L,
                            "NEW",
                            "",
                            new Address(
                                    "Junit Address1",
                                    "AddressLine2",
                                    "city",
                                    "state",
                                    "zipCode",
                                    "country"),
                            testDateTime,
                            BigDecimal.TEN,
                            new ArrayList<>());

            given(orderService.findOrderById(1L)).willReturn(Optional.of(new Order()));
            given(orderService.updateOrder(eq(orderRequest), any(Order.class)))
                    .willReturn(orderResponse);

            mockMvc.perform(
                            put("/api/orders/{id}", 1L)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(jsonMapper.writeValueAsString(orderRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.orderId", is(1)))
                    .andExpect(jsonPath("$.customerId", is(orderResponse.customerId()), Long.class))
                    .andExpect(jsonPath("$.status", is("NEW")))
                    .andExpect(jsonPath("$.source", is("")))
                    .andExpect(jsonPath("$.createdDate", notNullValue()))
                    .andExpect(jsonPath("$.totalPrice").value(is(10)))
                    .andExpect(jsonPath("$.deliveryAddress.addressLine1", is("Junit Address1")))
                    .andExpect(jsonPath("$.deliveryAddress.addressLine2", is("AddressLine2")))
                    .andExpect(jsonPath("$.deliveryAddress.city", is("city")))
                    .andExpect(jsonPath("$.deliveryAddress.state", is("state")))
                    .andExpect(jsonPath("$.deliveryAddress.zipCode", is("zipCode")))
                    .andExpect(jsonPath("$.deliveryAddress.country", is("country")));
        }

        @Test
        void shouldReturn404WhenUpdatingNonExistingOrder() throws Exception {
            Long orderId = 1L;
            given(orderService.findOrderById(orderId)).willReturn(Optional.empty());
            OrderRequest order =
                    new OrderRequest(
                            1L,
                            List.of(new OrderItemRequest("Product1", 10, BigDecimal.TEN)),
                            new Address(
                                    "Junit Address1",
                                    "AddressLine2",
                                    "city",
                                    "state",
                                    "zipCode",
                                    "country"));

            mockMvc.perform(
                            put("/api/orders/{id}", orderId)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(jsonMapper.writeValueAsString(order)))
                    .andExpect(status().isNotFound())
                    .andExpect(
                            header().string(
                                            "Content-Type",
                                            is(MediaType.APPLICATION_PROBLEM_JSON_VALUE)))
                    .andExpect(
                            jsonPath(
                                    "$.type", is("https://api.microservices.com/errors/not-found")))
                    .andExpect(jsonPath("$.title", is("Order Not Found")))
                    .andExpect(jsonPath("$.status", is(404)))
                    .andExpect(
                            jsonPath("$.detail")
                                    .value("Order with Id %d not found".formatted(orderId)));
        }
    }

    @Nested
    @DisplayName("delete methods")
    class Delete {
        @Test
        void shouldDeleteOrder() throws Exception {
            Long orderId = 1L;
            Order order = new Order().setId(orderId).setCustomerId(1L).setStatus(OrderStatus.NEW);
            given(orderService.findById(orderId)).willReturn(Optional.of(order));
            doNothing().when(orderService).deleteOrderById(orderId);

            mockMvc.perform(delete("/api/orders/{id}", orderId)).andExpect(status().isAccepted());

            // Verify that the service method was called with the correct ID
            verify(orderService).findById(orderId);
            verify(orderService).deleteOrderById(orderId);
            verifyNoMoreInteractions(orderService);
        }

        @Test
        void shouldReturn404WhenDeletingNonExistingOrder() throws Exception {
            Long orderId = 1L;
            given(orderService.findOrderById(orderId)).willReturn(Optional.empty());

            mockMvc.perform(delete("/api/orders/{id}", orderId))
                    .andExpect(status().isNotFound())
                    .andExpect(
                            header().string(
                                            "Content-Type",
                                            is(MediaType.APPLICATION_PROBLEM_JSON_VALUE)))
                    .andExpect(
                            jsonPath(
                                    "$.type", is("https://api.microservices.com/errors/not-found")))
                    .andExpect(jsonPath("$.title", is("Order Not Found")))
                    .andExpect(jsonPath("$.status", is(404)))
                    .andExpect(
                            jsonPath("$.detail")
                                    .value("Order with Id %d not found".formatted(orderId)));
        }
    }
}
