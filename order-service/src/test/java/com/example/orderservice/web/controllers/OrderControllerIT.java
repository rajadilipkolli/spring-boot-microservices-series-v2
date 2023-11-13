/***
<p>
    Licensed under MIT License Copyright (c) 2021-2023 Raja Kolli.
</p>
***/

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

import com.example.orderservice.common.AbstractIntegrationTest;
import com.example.orderservice.entities.Order;
import com.example.orderservice.entities.OrderItem;
import com.example.orderservice.entities.OrderStatus;
import com.example.orderservice.model.request.OrderItemRequest;
import com.example.orderservice.model.request.OrderRequest;
import com.example.orderservice.repositories.OrderRepository;
import com.example.orderservice.util.TestData;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class OrderControllerIT extends AbstractIntegrationTest {

    @Autowired private OrderRepository orderRepository;

    private List<Order> orderList = null;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();

        orderList = new ArrayList<>();
        Order order1 = TestData.getOrder();
        this.orderList.add(order1);
        Order order2 = new Order();
        order2.setCustomerId(1L);
        order2.setStatus(OrderStatus.NEW);
        this.orderList.add(order2);
        Order order3 = new Order();
        order3.setCustomerId(1L);
        order3.setStatus(OrderStatus.NEW);
        OrderItem orderItem2 = new OrderItem();
        orderItem2.setProductCode("Product3");
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
                .andExpect(jsonPath("$.size()", is(8)))
                .andExpect(jsonPath("$.data.size()", is(orderList.size())))
                .andExpect(jsonPath("$.totalElements", is(3)))
                .andExpect(jsonPath("$.pageNumber", is(1)))
                .andExpect(jsonPath("$.totalPages", is(1)))
                .andExpect(jsonPath("$.isFirst", is(true)))
                .andExpect(jsonPath("$.isLast", is(true)))
                .andExpect(jsonPath("$.hasNext", is(false)))
                .andExpect(jsonPath("$.hasPrevious", is(false)))
                .andExpect(
                        jsonPath("$.data[0].items.size()", is(orderList.get(0).getItems().size())));
    }

    @Test
    void shouldFindOrderById() throws Exception {
        Order order = orderList.get(0);
        Long orderId = order.getId();

        this.mockMvc
                .perform(get("/api/orders/{id}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId", is(orderId), Long.class))
                .andExpect(jsonPath("$.customerId", is(order.getCustomerId()), Long.class))
                .andExpect(jsonPath("$.status", is(order.getStatus().name())))
                .andExpect(jsonPath("$.source", is(order.getSource())))
                .andExpect(jsonPath("$.totalPrice").value("201.00"))
                .andExpect(jsonPath("$.items.size()", is(order.getItems().size())));
    }

    @Test
    void shouldCreateNewOrder() throws Exception {
        OrderRequest orderRequest =
                new OrderRequest(1L, List.of(new OrderItemRequest("Product1", 10, BigDecimal.TEN)));
        mockProductsExistsRequest(true, "Product1");

        this.mockMvc
                .perform(
                        post("/api/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(orderRequest)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.orderId", notNullValue()))
                .andExpect(jsonPath("$.customerId", is(orderRequest.customerId()), Long.class))
                .andExpect(jsonPath("$.status", is("NEW")))
                .andExpect(jsonPath("$.totalPrice").value("100.00"))
                .andExpect(jsonPath("$.items.size()", is(1)))
                .andExpect(jsonPath("$.items[0].itemId", notNullValue()))
                .andExpect(jsonPath("$.items[0].price", is(100.00)));
    }

    @Test
    void shouldCreateNewOrderFails() throws Exception {
        OrderRequest orderRequest =
                new OrderRequest(1L, List.of(new OrderItemRequest("Product2", 10, BigDecimal.TEN)));
        mockProductsExistsRequest(false, "Product2");

        this.mockMvc
                .perform(
                        post("/api/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(orderRequest)))
                .andExpect(status().isNotFound())
                .andExpect(
                        header().string(
                                        HttpHeaders.CONTENT_TYPE,
                                        is(MediaType.APPLICATION_PROBLEM_JSON_VALUE)))
                .andExpect(jsonPath("$.type", is("http://api.products.com/errors/not-found")))
                .andExpect(jsonPath("$.title", is("Product Not Found")))
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(
                        jsonPath("$.detail", is("One or More products Not found from [PRODUCT2]")))
                .andExpect(jsonPath("$.instance", is("/api/orders")))
                .andExpect(jsonPath("$.errorCategory").value("Generic"));
    }

    @Test
    void shouldReturn400WhenCreateNewOrderWithoutItems() throws Exception {
        OrderRequest orderRequest = new OrderRequest(-1L, new ArrayList<>());

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
                .andExpect(jsonPath("$.violations", hasSize(2)))
                .andExpect(jsonPath("$.violations[0].field", is("customerId")))
                .andExpect(jsonPath("$.violations[0].message", is("CustomerId should be positive")))
                .andExpect(jsonPath("$.violations[1].field", is("items")))
                .andExpect(jsonPath("$.violations[1].message", is("Order without items not valid")))
                .andReturn();
    }

    @Test
    void shouldUpdateOrder() throws Exception {
        mockProductsExistsRequest(true, "product1", "product4");
        Order order = orderList.get(0);

        OrderRequest orderDto = TestData.getOrderRequest(order);

        this.mockMvc
                .perform(
                        put("/api/orders/{id}", order.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(orderDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("NEW")))
                .andExpect(jsonPath("$.totalPrice").value("1211.00"))
                .andExpect(jsonPath("$.items.size()", is(2)))
                .andExpect(jsonPath("$.items[0].quantity", is(110)))
                .andExpect(jsonPath("$.items[0].price", is(1111.00)))
                .andExpect(jsonPath("$.items[1].quantity", is(100)))
                .andExpect(jsonPath("$.items[1].price", is(100.00)));
    }

    @Test
    void shouldDeleteOrder() throws Exception {
        Order order = orderList.get(0);

        this.mockMvc
                .perform(delete("/api/orders/{id}", order.getId()))
                .andExpect(status().isAccepted());
    }

    @Test
    void shouldFindOrdersByCustomersId() throws Exception {
        OrderItem orderItem = orderList.get(0).getItems().get(0);
        mockMvc.perform(
                        get("/api/orders/customer/{id}", orderList.get(0).getCustomerId())
                                .queryParam("page", "0")
                                .queryParam("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(orderList.size())))
                .andExpect(
                        jsonPath(
                                "$.data[0].customerId",
                                is(orderList.get(0).getCustomerId()),
                                Long.class))
                .andExpect(
                        jsonPath(
                                "$.data[0].items[0].price",
                                is(
                                        orderItem
                                                .getProductPrice()
                                                .multiply(new BigDecimal(orderItem.getQuantity()))),
                                BigDecimal.class));
    }
}
