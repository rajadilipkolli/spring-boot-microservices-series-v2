/* Licensed under Apache-2.0 2021-2022 */
package com.example.paymentservice.web.controllers;

import static org.hamcrest.CoreMatchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.paymentservice.common.AbstractIntegrationTest;
import com.example.paymentservice.entities.Customer;
import com.example.paymentservice.entities.Order;
import com.example.paymentservice.repositories.CustomerRepository;
import com.example.paymentservice.repositories.OrderRepository;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class OrderControllerIT extends AbstractIntegrationTest {

    @Autowired private OrderRepository orderRepository;
    @Autowired private CustomerRepository customerRepository;

    private List<Order> orderList = new ArrayList<>();
    private Customer customer;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        customerRepository.deleteAll();

        customer =
                customerRepository.save(
                        Customer.builder()
                                .name("junit")
                                .amountAvailable(0)
                                .amountReserved(0)
                                .build());

        orderList = new ArrayList<>();
        this.orderList.add(
                new Order(
                        null,
                        "junit1@email.com",
                        "text 1",
                        customer.getId(),
                        null,
                        null,
                        new ArrayList<>()));
        this.orderList.add(
                new Order(
                        null,
                        "junit2@email.com",
                        "text 2",
                        customer.getId(),
                        null,
                        null,
                        new ArrayList<>()));
        this.orderList.add(
                new Order(
                        null,
                        "junit3@email.com",
                        "text 3",
                        customer.getId(),
                        null,
                        null,
                        new ArrayList<>()));
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
                .andExpect(jsonPath("$.customerEmail", is(order.getCustomerEmail())));
    }

    @Test
    void shouldCreateNewOrder() throws Exception {
        Order order =
                new Order(
                        null,
                        "junit1@email.com",
                        "text 1",
                        customer.getId(),
                        null,
                        null,
                        new ArrayList<>());
        this.mockMvc
                .perform(
                        post("/api/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(order)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerEmail", is(order.getCustomerEmail())));
    }

    @Test
    void shouldReturn400WhenCreateNewOrderWithoutEmail() throws Exception {
        Order order =
                new Order(null, null, "text 1", customer.getId(), null, null, new ArrayList<>());
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
        order.setCustomerAddress("Updated Order");
        order.setCustomerEmail("junit5@email.com");

        this.mockMvc
                .perform(
                        put("/api/orders/{id}", order.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(order)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerEmail", is(order.getCustomerEmail())));
    }
}
