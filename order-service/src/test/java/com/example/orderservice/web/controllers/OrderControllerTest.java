package com.example.orderservice.web.controllers;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.orderservice.dtos.OrderDto;
import com.example.orderservice.entities.Order;
import com.example.orderservice.services.OrderService;
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
import org.zalando.problem.ProblemModule;
import org.zalando.problem.violations.ConstraintViolationProblemModule;

@WebMvcTest(controllers = OrderController.class)
@ActiveProfiles("test")
class OrderControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private OrderService orderService;

    @Autowired private ObjectMapper objectMapper;

    private List<Order> orderList;

    @BeforeEach
    void setUp() {
        this.orderList = new ArrayList<>();
        this.orderList.add(new Order(1L, "email1@junit.com", "address 1", new ArrayList<>()));
        this.orderList.add(new Order(2L, "email2@junit.com", "address 2", new ArrayList<>()));
        this.orderList.add(new Order(3L, "email3@junit.com", "address 3", new ArrayList<>()));

        objectMapper.registerModule(new ProblemModule());
        objectMapper.registerModule(new ConstraintViolationProblemModule());
    }

    @Test
    void shouldFetchAllOrders() throws Exception {

        List<OrderDto> orderListDto = new ArrayList<>();
        orderListDto.add(new OrderDto(null, "email1@junit.com", "address 1", new ArrayList<>()));
        orderListDto.add(new OrderDto(null, "email2@junit.com", "address 2", new ArrayList<>()));
        orderListDto.add(new OrderDto(null, "email3@junit.com", "address 3", new ArrayList<>()));
        given(orderService.findAllOrders()).willReturn(orderListDto);

        this.mockMvc
                .perform(get("/api/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()", is(orderList.size())));
    }

    @Test
    void shouldFindOrderById() throws Exception {
        Long orderId = 1L;
        OrderDto order = new OrderDto(null, "email1@junit.com", "address 1", new ArrayList<>());
        given(orderService.findOrderById(orderId)).willReturn(Optional.of(order));

        this.mockMvc
                .perform(get("/api/orders/{id}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerAddress", is(order.getCustomerAddress())));
    }

    @Test
    void shouldReturn404WhenFetchingNonExistingOrder() throws Exception {
        Long orderId = 1L;
        given(orderService.findOrderById(orderId)).willReturn(Optional.empty());

        this.mockMvc.perform(get("/api/orders/{id}", orderId)).andExpect(status().isNotFound());
    }

    @Test
    void shouldCreateNewOrder() throws Exception {
        given(orderService.saveOrder(any(OrderDto.class)))
                .willAnswer((invocation) -> invocation.getArgument(0));

        OrderDto orderDto = new OrderDto(10L, "email1@junit.com", "address 1", new ArrayList<>());

        this.mockMvc
                .perform(
                        post("/api/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(orderDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId", notNullValue()))
                .andExpect(jsonPath("$.customerAddress", is(orderDto.getCustomerAddress())));
    }

    @Test
    void shouldReturn400WhenCreateNewOrderWithoutText() throws Exception {
        Order order = new Order(null, null, null, null);

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
                .andExpect(jsonPath("$.violations[0].message", is("Email can't be blank")))
                .andReturn();
    }

    @Test
    void shouldUpdateOrder() throws Exception {
        OrderDto orderDto =
                new OrderDto(1L, "email1@junit.com", "address updated", new ArrayList<>());

        given(orderService.findOrderById(orderDto.getOrderId())).willReturn(Optional.of(orderDto));
        given(orderService.saveOrder(any(OrderDto.class)))
                .willAnswer((invocation) -> invocation.getArgument(0));

        this.mockMvc
                .perform(
                        put("/api/orders/{id}", orderDto.getOrderId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(orderDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerAddress", is(orderDto.getCustomerAddress())));
    }

    @Test
    void shouldReturn404WhenUpdatingNonExistingOrder() throws Exception {
        Long orderId = 1L;
        given(orderService.findOrderById(orderId)).willReturn(Optional.empty());
        Order order = new Order(1L, "email1@junit.com", "address updated", new ArrayList<>());

        this.mockMvc
                .perform(
                        put("/api/orders/{id}", orderId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(order)))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldDeleteOrder() throws Exception {
        Long orderId = 1L;
        OrderDto order =
                new OrderDto(null, "email1@junit.com", "address updated", new ArrayList<>());
        given(orderService.findOrderById(orderId)).willReturn(Optional.of(order));
        doNothing().when(orderService).deleteOrderById(orderId);

        this.mockMvc
                .perform(delete("/api/orders/{id}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerAddress", is(order.getCustomerAddress())));
    }

    @Test
    void shouldReturn404WhenDeletingNonExistingOrder() throws Exception {
        Long orderId = 1L;
        given(orderService.findOrderById(orderId)).willReturn(Optional.empty());

        this.mockMvc.perform(delete("/api/orders/{id}", orderId)).andExpect(status().isNotFound());
    }
}
