/* Licensed under Apache-2.0 2021-2022 */
package com.example.paymentservice.web.controllers;

import static com.example.paymentservice.utils.AppConstants.PROFILE_TEST;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.orderservice.dtos.OrderDto;
import com.example.paymentservice.entities.Order;
import com.example.paymentservice.services.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = OrderController.class)
@ActiveProfiles(PROFILE_TEST)
class OrderControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private OrderService orderService;

    @Autowired private ObjectMapper objectMapper;

    private List<Order> orderList;
    private List<OrderDto> orderListDto;

    @BeforeEach
    void setUp() {
        this.orderList = new ArrayList<>();
        this.orderList.add(
                new Order(
                        1L, "junit1@email.com", "Updated Text", 1L, null, null, new ArrayList<>()));
        this.orderList.add(
                new Order(
                        2L, "junit2@email.com", "Updated Text", 1L, null, null, new ArrayList<>()));
        this.orderList.add(
                new Order(
                        3L, "junit3@email.com", "Updated Text", 1L, null, null, new ArrayList<>()));

        this.orderListDto = new ArrayList<>();
        this.orderListDto.add(
                OrderDto.builder()
                        .orderId(1L)
                        .customerAddress("text 1")
                        .customerEmail("junit1@email.com")
                        .customerId(1L)
                        .build());
        this.orderListDto.add(
                OrderDto.builder()
                        .orderId(2L)
                        .customerAddress("text 2")
                        .customerEmail("junit2@email.com")
                        .customerId(1L)
                        .build());
        this.orderListDto.add(
                OrderDto.builder()
                        .orderId(3L)
                        .customerAddress("text 3")
                        .customerEmail("junit3@email.com")
                        .customerId(1L)
                        .build());
    }

    @Test
    void shouldFetchAllOrders() throws Exception {

        given(orderService.findAllOrders()).willReturn(this.orderListDto);

        this.mockMvc
                .perform(get("/api/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()", is(orderList.size())));
    }

    @Test
    void shouldFindOrderById() throws Exception {
        Long orderId = 1L;
        Order order =
                new Order(
                        orderId,
                        "junit1@email.com",
                        "Updated Text",
                        1L,
                        null,
                        null,
                        new ArrayList<>());
        given(orderService.findOrderById(orderId)).willReturn(Optional.of(orderListDto.get(0)));

        this.mockMvc
                .perform(get("/api/orders/{id}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerEmail", is(order.getCustomerEmail())));
    }

    @Test
    void shouldReturn404WhenFetchingNonExistingOrder() throws Exception {
        Long orderId = 1L;
        given(orderService.findOrderById(orderId)).willReturn(Optional.empty());

        this.mockMvc.perform(get("/api/orders/{id}", orderId)).andExpect(status().isNotFound());
    }

    @Test
    void shouldCreateNewOrder() throws Exception {
        given(orderService.saveOrder(any(Order.class)))
                .willAnswer((invocation) -> invocation.getArgument(0));

        Order order =
                new Order(
                        100L,
                        "junit1@email.com",
                        "Updated Text",
                        1L,
                        null,
                        null,
                        new ArrayList<>());
        this.mockMvc
                .perform(
                        post("/api/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(order)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.customerEmail", is(order.getCustomerEmail())));
    }

    @Test
    void shouldReturn400WhenCreateNewOrderWithoutCustomerEmail() throws Exception {
        Order order = new Order(null, null, "junit1@email.com", 1L, null, null, new ArrayList<>());

        this.mockMvc
                .perform(
                        post("/api/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(order)))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", is("application/problem+json")))
                .andExpect(jsonPath("$.type", is("about:blank")))
                .andExpect(jsonPath("$.title", is("Bad Request")))
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.detail", is("Invalid request content.")))
                .andExpect(jsonPath("$.instance", is("/api/orders")))
                .andReturn();
    }

    @Test
    void shouldUpdateOrder() throws Exception {
        Long orderId = 1L;
        Order order =
                new Order(
                        orderId,
                        "junit1@email.com",
                        "Updated Text",
                        1L,
                        null,
                        null,
                        new ArrayList<>());
        given(orderService.findOrderById(orderId)).willReturn(Optional.of(orderListDto.get(0)));
        given(orderService.saveOrder(any(Order.class)))
                .willAnswer((invocation) -> invocation.getArgument(0));

        this.mockMvc
                .perform(
                        put("/api/orders/{id}", order.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(order)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerEmail", is(order.getCustomerEmail())));
    }

    @Test
    void shouldReturn404WhenUpdatingNonExistingOrder() throws Exception {
        Long orderId = 1L;
        given(orderService.findOrderById(orderId)).willReturn(Optional.empty());
        Order order =
                new Order(
                        orderId,
                        "Updated text",
                        "junit1@email.com",
                        1L,
                        null,
                        null,
                        new ArrayList<>());

        this.mockMvc
                .perform(
                        put("/api/orders/{id}", orderId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(order)))
                .andExpect(status().isNotFound());
    }
}
