/* Licensed under Apache-2.0 2021-2022 */
package com.example.orderservice.web.controllers;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.common.dtos.OrderDto;
import com.example.common.dtos.OrderItemDto;
import com.example.orderservice.common.AbstractIntegrationTest;
import com.example.orderservice.entities.Order;
import com.example.orderservice.entities.OrderItem;
import com.example.orderservice.mapper.OrderMapper;
import com.example.orderservice.repositories.OrderRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class OrderControllerIT extends AbstractIntegrationTest {

    @Autowired private OrderRepository orderRepository;

    @Autowired private OrderMapper orderMapper;

    private List<Order> orderList = null;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();

        orderList = new ArrayList<>();
        Order order1 = new Order(null, 1L, "NEW", null, new ArrayList<>());
        OrderItem orderItem = new OrderItem();
        orderItem.setProductId("Product1");
        orderItem.setQuantity(10);
        orderItem.setProductPrice(BigDecimal.TEN);
        order1.addOrderItem(orderItem);
        this.orderList.add(order1);
        Order order2 = new Order(null, 1L, "NEW", null, new ArrayList<>());
        OrderItem orderItem1 = new OrderItem();
        orderItem1.setProductId("Product2");
        orderItem1.setQuantity(100);
        orderItem1.setProductPrice(BigDecimal.ONE);
        order2.addOrderItem(orderItem1);
        this.orderList.add(order2);
        Order order3 = new Order(null, 1L, "NEW", null, new ArrayList<>());
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
                .andExpect(jsonPath("$.customerId", is(order.getCustomerId()), Long.class))
                .andExpect(jsonPath("$.status", is(order.getStatus())))
                .andExpect(jsonPath("$.items.size()", is(order.getItems().size())));
    }

    @Test
    void shouldCreateNewOrder() throws Exception {
        mockProductExistsRequest(true);
        OrderDto orderDto = new OrderDto(null, 1L, "NEW", "", new ArrayList<>());
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
                .andExpect(jsonPath("$.customerId", is(orderDto.getCustomerId()), Long.class))
                .andExpect(jsonPath("$.status", is(orderDto.getStatus())))
                .andExpect(header().exists("Location"));
    }

    @Test
    void shouldReturn400WhenCreateNewOrderWithoutItems() throws Exception {
        OrderDto order = new OrderDto(null, 0L, null, null, new ArrayList<>());

        this.mockMvc
                .perform(
                        post("/api/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(order)))
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
        mockProductExistsRequest(true);
        Order order = orderList.get(0);

        OrderDto orderDto = this.orderMapper.toDto(order);
        orderDto.setStatus("Completed");

        this.mockMvc
                .perform(
                        put("/api/orders/{id}", order.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(orderDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is(orderDto.getStatus())));
    }

    @Test
    void shouldDeleteOrder() throws Exception {
        Order order = orderList.get(0);

        this.mockMvc
                .perform(delete("/api/orders/{id}", order.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is(order.getStatus())));
    }
}
