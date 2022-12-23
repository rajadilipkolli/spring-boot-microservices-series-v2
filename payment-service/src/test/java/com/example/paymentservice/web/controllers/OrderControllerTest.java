/* Licensed under Apache-2.0 2021-2022 */
package com.example.paymentservice.web.controllers;

import static com.example.paymentservice.utils.AppConstants.PROFILE_TEST;
import static org.hamcrest.CoreMatchers.is;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.common.dtos.OrderDto;
import com.example.paymentservice.entities.Order;
import com.example.paymentservice.services.OrderService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = OrderController.class)
@ActiveProfiles(PROFILE_TEST)
class OrderControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private OrderService orderService;

    private List<Order> orderList;
    private List<OrderDto> orderListDto;

    @BeforeEach
    void setUp() {
        this.orderList = new ArrayList<>();
        this.orderList.add(new Order(1L, 1L, null, null, new ArrayList<>()));
        this.orderList.add(new Order(2L, 1L, null, null, new ArrayList<>()));
        this.orderList.add(new Order(3L, 1L, null, null, new ArrayList<>()));

        this.orderListDto = new ArrayList<>();
        this.orderListDto.add(OrderDto.builder().orderId(1L).customerId(1L).build());
        this.orderListDto.add(OrderDto.builder().orderId(2L).customerId(1L).build());
        this.orderListDto.add(OrderDto.builder().orderId(3L).customerId(1L).build());
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
        Order order = new Order(orderId, 1L, null, null, new ArrayList<>());
        given(orderService.findOrderById(orderId)).willReturn(Optional.of(orderListDto.get(0)));

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
}
