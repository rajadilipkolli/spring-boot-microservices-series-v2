/* Licensed under Apache-2.0 2021-2022 */
package com.example.orderservice.web.controllers;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.orderservice.dtos.OrderDto;
import com.example.orderservice.dtos.OrderItemDto;
import com.example.orderservice.entities.Order;
import com.example.orderservice.entities.OrderItem;
import com.example.orderservice.mapper.OrderMapper;
import com.example.orderservice.repositories.OrderRepository;
import com.example.orderservice.utils.AppConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles({AppConstants.PROFILE_TEST, AppConstants.PROFILE_IT})
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Disabled
class OrderControllerIT {

    @Autowired private OrderRepository orderRepository;

    @Autowired private OrderMapper orderMapper;

    @Autowired private MockMvc mockMvc;

    @Autowired private ObjectMapper objectMapper;

    private List<Order> orderList = null;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();

        orderList = new ArrayList<>();
        Order order1 =
                Order.builder()
                        .customerEmail("email1@junit.com")
                        .customerAddress("address 1")
                        .customerId(1L)
                        .build();
        OrderItem orderItem = new OrderItem();
        orderItem.setProductId("Product1");
        orderItem.setQuantity(10);
        orderItem.setProductPrice(BigDecimal.TEN);
        order1.addOrderItem(orderItem);
        this.orderList.add(order1);
        Order order2 =
                Order.builder()
                        .customerEmail("email2@junit.com")
                        .customerAddress("address 2")
                        .customerId(2L)
                        .build();
        OrderItem orderItem1 = new OrderItem();
        orderItem1.setProductId("Product2");
        orderItem1.setQuantity(100);
        orderItem1.setProductPrice(BigDecimal.ONE);
        order2.addOrderItem(orderItem1);
        this.orderList.add(order2);
        Order order3 =
                Order.builder()
                        .customerEmail("email3@junit.com")
                        .customerAddress("address 3")
                        .customerId(3L)
                        .build();
        OrderItem orderItem2 = new OrderItem();
        orderItem2.setProductId("Product2");
        orderItem2.setQuantity(100);
        orderItem2.setProductPrice(BigDecimal.ONE);
        order3.addOrderItem(orderItem2);
        this.orderList.add(order3);

        orderList = orderRepository.saveAll(orderList);
    }

    @Test
    void shouldFetchAllOrders() throws Exception {
        this.mockMvc
                .perform(get("/api/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()", is(orderList.size())));
    }

    @Test
    void shouldFindOrderById() throws Exception {
        Order order = orderList.get(0);
        Long orderId = order.getId();

        this.mockMvc
                .perform(get("/api/orders/{id}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerAddress", is(order.getCustomerAddress())))
                .andExpect(jsonPath("$.customerEmail", is(order.getCustomerEmail())))
                .andExpect(jsonPath("$.items.size()", is(order.getItems().size())));
    }

    @Test
    void shouldCreateNewOrder() throws Exception {
        OrderDto orderDto =
                new OrderDto(
                        null, "email1@junit.com", "address 1", 1, "NEW", "", new ArrayList<>());
        OrderItemDto orderItemDto = new OrderItemDto();
        orderItemDto.setProductId("Product1");
        orderItemDto.setQuantity(10);
        orderItemDto.setProductPrice(BigDecimal.TEN);
        orderDto.setItems(List.of(orderItemDto));
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
    void shouldReturn400WhenCreateNewOrderWithoutEmail() throws Exception {
        OrderDto order = new OrderDto(null, null, null, 0, null, null, null);

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
        Order order = orderList.get(0);

        OrderDto orderDto = this.orderMapper.toDto(order);
        orderDto.setCustomerAddress("Updated Address");

        this.mockMvc
                .perform(
                        put("/api/orders/{id}", order.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(orderDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerAddress", is(orderDto.getCustomerAddress())));
    }

    @Test
    void shouldDeleteOrder() throws Exception {
        Order order = orderList.get(0);

        this.mockMvc
                .perform(delete("/api/orders/{id}", order.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerAddress", is(order.getCustomerAddress())));
    }
}
