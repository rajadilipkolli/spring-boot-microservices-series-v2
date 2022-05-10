package com.example.paymentservice.web.controllers;

import static com.example.paymentservice.utils.AppConstants.PROFILE_TEST;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.zalando.problem.jackson.ProblemModule;
import org.zalando.problem.violations.ConstraintViolationProblemModule;

@WebMvcTest(controllers = OrderController.class)
@ActiveProfiles(PROFILE_TEST)
class OrderControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private OrderService orderService;

    @Autowired private ObjectMapper objectMapper;

    private List<Order> orderList;

    @BeforeEach
    void setUp() {
        this.orderList = new ArrayList<>();
        this.orderList.add(
                Order.builder()
                        .id(1L)
                        .customerAddress("text 1")
                        .customerEmail("junit1@email.com")
                        .customerId(1L)
                        .build());
        this.orderList.add(
                Order.builder()
                        .id(2L)
                        .customerAddress("text 2")
                        .customerEmail("junit2@email.com")
                        .customerId(1L)
                        .build());
        this.orderList.add(
                Order.builder()
                        .id(3L)
                        .customerAddress("text 3")
                        .customerEmail("junit3@email.com")
                        .customerId(1L)
                        .build());

        objectMapper.registerModule(new ProblemModule());
        objectMapper.registerModule(new ConstraintViolationProblemModule());
    }

    @Test
    void shouldFetchAllOrders() throws Exception {
        given(orderService.findAllOrders()).willReturn(this.orderList);

        this.mockMvc
                .perform(get("/api/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()", is(orderList.size())));
    }

    @Test
    void shouldFindOrderById() throws Exception {
        Long orderId = 1L;
        Order order =
                Order.builder()
                        .id(orderId)
                        .customerAddress("text 1")
                        .customerEmail("junit1@email.com")
                        .customerId(1L)
                        .build();
        given(orderService.findOrderById(orderId)).willReturn(Optional.of(order));

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
                Order.builder()
                        .id(1L)
                        .customerAddress("text 1")
                        .customerEmail("junit1@email.com")
                        .customerId(1L)
                        .build();
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
    void shouldReturn400WhenCreateNewOrderWithoutText() throws Exception {
        Order order =
                Order.builder()
                        .id(null)
                        .customerAddress("text 1")
                        .customerEmail(null)
                        .customerId(1L)
                        .build();

        this.mockMvc
                .perform(
                        post("/api/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(order)))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", is("application/problem+json")))
                .andExpect(
                        jsonPath(
                                "$.type",
                                is("https://zalando.github.io/problem/constraint-violation")))
                .andExpect(jsonPath("$.title", is("Constraint Violation")))
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.violations", hasSize(1)))
                .andExpect(jsonPath("$.violations[0].field", is("customerEmail")))
                .andExpect(jsonPath("$.violations[0].message", is("Email cannot be empty")))
                .andReturn();
    }

    @Test
    void shouldUpdateOrder() throws Exception {
        Long orderId = 1L;
        Order order =
                Order.builder()
                        .id(orderId)
                        .customerAddress("updated Text")
                        .customerEmail("junit1@email.com")
                        .customerId(1L)
                        .build();
        given(orderService.findOrderById(orderId)).willReturn(Optional.of(order));
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
                Order.builder()
                        .id(orderId)
                        .customerAddress("Updated text")
                        .customerEmail("junit1@email.com")
                        .customerId(1L)
                        .build();

        this.mockMvc
                .perform(
                        put("/api/orders/{id}", orderId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(order)))
                .andExpect(status().isNotFound());
    }
}
