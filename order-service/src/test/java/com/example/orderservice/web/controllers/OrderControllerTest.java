/***
<p>
    Licensed under MIT License Copyright (c) 2021-2023 Raja Kolli.
</p>
***/

package com.example.orderservice.web.controllers;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.common.dtos.OrderDto;
import com.example.common.dtos.OrderItemDto;
import com.example.orderservice.entities.Order;
import com.example.orderservice.model.request.OrderItemRequest;
import com.example.orderservice.model.request.OrderRequest;
import com.example.orderservice.model.response.PagedResult;
import com.example.orderservice.services.OrderGeneratorService;
import com.example.orderservice.services.OrderKafkaStreamService;
import com.example.orderservice.services.OrderService;
import com.example.orderservice.utils.AppConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = OrderController.class)
@ActiveProfiles(AppConstants.PROFILE_TEST)
class OrderControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private OrderService orderService;

    @MockBean private OrderGeneratorService orderGeneratorService;

    @MockBean private OrderKafkaStreamService orderKafkaStreamService;

    @Autowired private ObjectMapper objectMapper;

    @Test
    void shouldFetchAllOrders() throws Exception {

        List<OrderDto> orderListDto = new ArrayList<>();
        orderListDto.add(new OrderDto(null, 1L, "NEW", "", new ArrayList<>()));
        orderListDto.add(new OrderDto(null, 1L, "NEW", "", new ArrayList<>()));
        orderListDto.add(new OrderDto(null, 1L, "NEW", "", new ArrayList<>()));
        Page<OrderDto> page = new PageImpl<>(orderListDto);
        PagedResult<OrderDto> inventoryPagedResult = new PagedResult<>(page);

        given(orderService.findAllOrders(0, 10, "id", "asc")).willReturn(inventoryPagedResult);

        this.mockMvc
                .perform(get("/api/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()", is(8)))
                .andExpect(jsonPath("$.data.size()", is(orderListDto.size())))
                .andExpect(jsonPath("$.totalElements", is(3)))
                .andExpect(jsonPath("$.pageNumber", is(1)))
                .andExpect(jsonPath("$.totalPages", is(1)))
                .andExpect(jsonPath("$.isFirst", is(true)))
                .andExpect(jsonPath("$.isLast", is(true)))
                .andExpect(jsonPath("$.hasNext", is(false)))
                .andExpect(jsonPath("$.hasPrevious", is(false)));
    }

    @Test
    void shouldFindOrderById() throws Exception {
        Long orderId = 1L;
        OrderDto order = new OrderDto(1L, 1L, "NEW", "", new ArrayList<>());
        given(orderService.findOrderById(orderId)).willReturn(Optional.of(order));

        this.mockMvc
                .perform(get("/api/orders/{id}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId", is(order.getCustomerId()), Long.class));
    }

    @Test
    void shouldReturn404WhenFetchingNonExistingOrder() throws Exception {
        Long orderId = 1L;
        given(orderService.findOrderById(orderId)).willReturn(Optional.empty());

        this.mockMvc.perform(get("/api/orders/{id}", orderId)).andExpect(status().isNotFound());
    }

    @Test
    void shouldCreateNewOrder() throws Exception {
        OrderDto orderDto = new OrderDto(10L, 1L, "NEW", "", new ArrayList<>());
        OrderItemDto orderItemDto = new OrderItemDto();
        orderItemDto.setProductId("Product1");
        orderItemDto.setQuantity(10);
        orderItemDto.setProductPrice(BigDecimal.TEN);
        orderDto.setItems(List.of(orderItemDto));

        OrderRequest orderRequest =
                new OrderRequest(1L, List.of(new OrderItemRequest("Product1", 10, BigDecimal.TEN)));

        given(orderService.saveOrder(any(OrderRequest.class))).willReturn(orderDto);

        this.mockMvc
                .perform(
                        post("/api/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(orderRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId", notNullValue()))
                .andExpect(jsonPath("$.customerId", is(orderDto.getCustomerId()), Long.class))
                .andExpect(jsonPath("$.items.size()", is(1)));
    }

    @Test
    void shouldFailCreatingNewOrder() throws Exception {

        OrderRequest orderRequest = new OrderRequest(1L, new ArrayList<>());

        this.mockMvc
                .perform(
                        post("/api/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(orderRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", is("application/problem+json")))
                .andExpect(jsonPath("$.type", is("about:blank")))
                .andExpect(jsonPath("$.title", is("Constraint Violation")))
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.detail", is("Invalid request content.")))
                .andExpect(jsonPath("$.instance", is("/api/orders")))
                .andExpect(jsonPath("$.violations", hasSize(1)))
                .andExpect(jsonPath("$.violations[0].field", is("items")))
                .andExpect(jsonPath("$.violations[0].message", is("Order without items not valid")))
                .andReturn();
    }

    @Test
    void shouldReturn400WhenCreateNewOrderWithoutCustomerId() throws Exception {
        OrderDto orderDto = new OrderDto();
        orderDto.setCustomerId(0L);

        this.mockMvc
                .perform(
                        post("/api/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(orderDto)))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", is("application/problem+json")))
                .andExpect(jsonPath("$.type", is("about:blank")))
                .andExpect(jsonPath("$.title", is("Constraint Violation")))
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.detail", is("Invalid request content.")))
                .andExpect(jsonPath("$.instance", is("/api/orders")))
                .andExpect(jsonPath("$.violations", hasSize(2)))
                .andExpect(jsonPath("$.violations[0].field", is("customerId")))
                .andExpect(jsonPath("$.violations[0].message", is("CustomerId should be positive")))
                .andExpect(jsonPath("$.violations[1].field", is("items")))
                .andExpect(jsonPath("$.violations[1].message", is("Order without items not valid")))
                .andReturn();
    }

    @Test
    void shouldUpdateOrder() throws Exception {
        OrderDto orderDto = new OrderDto(1L, 1L, "NEW", "", new ArrayList<>());

        OrderRequest orderRequest =
                new OrderRequest(1L, List.of(new OrderItemRequest("Product1", 10, BigDecimal.TEN)));

        given(orderService.findById(orderDto.getOrderId())).willReturn(Optional.of(new Order()));
        given(orderService.updateOrder(eq(orderRequest), any(Order.class))).willReturn(orderDto);

        this.mockMvc
                .perform(
                        put("/api/orders/{id}", orderDto.getOrderId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(orderRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId", is(orderDto.getCustomerId()), Long.class));
    }

    @Test
    void shouldReturn404WhenUpdatingNonExistingOrder() throws Exception {
        Long orderId = 1L;
        given(orderService.findOrderById(orderId)).willReturn(Optional.empty());
        Order order = new Order(1L, 1L, "CONFIRMED", null, new ArrayList<>());

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
        OrderDto order = new OrderDto(null, 1L, "NEW", "", new ArrayList<>());
        given(orderService.findOrderById(orderId)).willReturn(Optional.of(order));
        doNothing().when(orderService).deleteOrderById(orderId);

        this.mockMvc
                .perform(delete("/api/orders/{id}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId", is(order.getCustomerId()), Long.class));
    }

    @Test
    void shouldReturn404WhenDeletingNonExistingOrder() throws Exception {
        Long orderId = 1L;
        given(orderService.findOrderById(orderId)).willReturn(Optional.empty());

        this.mockMvc.perform(delete("/api/orders/{id}", orderId)).andExpect(status().isNotFound());
    }
}
