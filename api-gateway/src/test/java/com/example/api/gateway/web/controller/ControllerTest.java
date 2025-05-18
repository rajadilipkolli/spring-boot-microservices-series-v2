/*
 * Testing frameworks:
 * - JUnit 5 (org.junit.jupiter.api)
 * - Spring Boot Starter Test (spring-boot-starter-test)
 * - Mockito (org.mockito)
 */
package com.example.api.gateway.web.controller;

import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;

import java.util.Optional;
import java.util.Collections;
import java.util.Arrays;
import java.util.List;

import com.example.api.gateway.service.UserService;
import com.example.api.gateway.service.OrderService;
import com.example.api.gateway.web.dto.UserDto;
import com.example.api.gateway.web.dto.OrderDto;
import com.example.api.gateway.web.controller.UserController;
import com.example.api.gateway.web.controller.OrderController;
import com.example.api.gateway.exception.ResourceNotFoundException;

@WebMvcTest(controllers = {
    UserController.class,
    OrderController.class
})
class ControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    @MockBean
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        Mockito.reset(userService, orderService);
    }

    private String toJson(Object obj) throws Exception {
        return objectMapper.writeValueAsString(obj);
    }

    @Test
    void shouldReturnUser_whenUserExists() throws Exception {
        UserDto user = new UserDto(1L, "alice");
        when(userService.findById(1L)).thenReturn(Optional.of(user));
        mockMvc.perform(get("/users/1")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.name").value("alice"));
    }

    @Test
    void shouldReturnNotFound_whenUserDoesNotExist() throws Exception {
        when(userService.findById(1L)).thenReturn(Optional.empty());
        mockMvc.perform(get("/users/1")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnUsers_whenUsersExist() throws Exception {
        UserDto u1 = new UserDto(1L, "alice");
        UserDto u2 = new UserDto(2L, "bob");
        when(userService.findAll()).thenReturn(Arrays.asList(u1, u2));
        mockMvc.perform(get("/users")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(1))
            .andExpect(jsonPath("$[1].id").value(2));
    }

    @Test
    void shouldReturnEmptyList_whenNoUsers() throws Exception {
        when(userService.findAll()).thenReturn(Collections.emptyList());
        mockMvc.perform(get("/users")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void shouldCreateUser_whenValidInput() throws Exception {
        UserDto toCreate = new UserDto(null, "charlie");
        UserDto created = new UserDto(3L, "charlie");
        when(userService.create(any(UserDto.class))).thenReturn(created);
        mockMvc.perform(post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(toCreate)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(3))
            .andExpect(jsonPath("$.name").value("charlie"));
    }

    @Test
    void shouldReturnBadRequest_whenCreateUserInvalidInput() throws Exception {
        UserDto toCreate = new UserDto();
        mockMvc.perform(post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(toCreate)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void shouldUpdateUser_whenUserExists() throws Exception {
        UserDto toUpdate = new UserDto(1L, "aliceUpdated");
        when(userService.update(eq(1L), any(UserDto.class))).thenReturn(Optional.of(toUpdate));
        mockMvc.perform(put("/users/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(toUpdate)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("aliceUpdated"));
    }

    @Test
    void shouldReturnNotFound_whenUpdateUserDoesNotExist() throws Exception {
        UserDto toUpdate = new UserDto(99L, "nonexistent");
        when(userService.update(eq(99L), any(UserDto.class))).thenReturn(Optional.empty());
        mockMvc.perform(put("/users/99")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(toUpdate)))
            .andExpect(status().isNotFound());
    }

    @Test
    void shouldDeleteUser_whenUserExists() throws Exception {
        doNothing().when(userService).delete(1L);
        mockMvc.perform(delete("/users/1"))
            .andExpect(status().isNoContent());
    }

    @Test
    void shouldReturnNotFound_whenDeleteUserDoesNotExist() throws Exception {
        doThrow(new ResourceNotFoundException("User not found")).when(userService).delete(99L);
        mockMvc.perform(delete("/users/99"))
            .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnOrder_whenOrderExists() throws Exception {
        OrderDto order = new OrderDto(1L, "item1", 2);
        when(orderService.findById(1L)).thenReturn(Optional.of(order));
        mockMvc.perform(get("/orders/1")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.item").value("item1"))
            .andExpect(jsonPath("$.quantity").value(2));
    }

    @Test
    void shouldReturnNotFound_whenOrderDoesNotExist() throws Exception {
        when(orderService.findById(1L)).thenReturn(Optional.empty());
        mockMvc.perform(get("/orders/1")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnOrders_whenOrdersExist() throws Exception {
        OrderDto o1 = new OrderDto(1L, "item1", 2);
        OrderDto o2 = new OrderDto(2L, "item2", 3);
        when(orderService.findAll()).thenReturn(Arrays.asList(o1, o2));
        mockMvc.perform(get("/orders")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(1))
            .andExpect(jsonPath("$[1].id").value(2));
    }

    @Test
    void shouldReturnEmptyList_whenNoOrders() throws Exception {
        when(orderService.findAll()).thenReturn(Collections.emptyList());
        mockMvc.perform(get("/orders")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void shouldCreateOrder_whenValidInput() throws Exception {
        OrderDto toCreate = new OrderDto(null, "item3", 1);
        OrderDto created = new OrderDto(3L, "item3", 1);
        when(orderService.create(any(OrderDto.class))).thenReturn(created);
        mockMvc.perform(post("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(toCreate)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(3))
            .andExpect(jsonPath("$.item").value("item3"))
            .andExpect(jsonPath("$.quantity").value(1));
    }

    @Test
    void shouldReturnBadRequest_whenCreateOrderInvalidInput() throws Exception {
        OrderDto toCreate = new OrderDto();
        mockMvc.perform(post("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(toCreate)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void shouldUpdateOrder_whenOrderExists() throws Exception {
        OrderDto toUpdate = new OrderDto(1L, "itemUpdated", 5);
        when(orderService.update(eq(1L), any(OrderDto.class))).thenReturn(Optional.of(toUpdate));
        mockMvc.perform(put("/orders/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(toUpdate)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.item").value("itemUpdated"))
            .andExpect(jsonPath("$.quantity").value(5));
    }

    @Test
    void shouldReturnNotFound_whenUpdateOrderDoesNotExist() throws Exception {
        OrderDto toUpdate = new OrderDto(99L, "nonexistent", 1);
        when(orderService.update(eq(99L), any(OrderDto.class))).thenReturn(Optional.empty());
        mockMvc.perform(put("/orders/99")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(toUpdate)))
            .andExpect(status().isNotFound());
    }

    @Test
    void shouldDeleteOrder_whenOrderExists() throws Exception {
        doNothing().when(orderService).delete(1L);
        mockMvc.perform(delete("/orders/1"))
            .andExpect(status().isNoContent());
    }

    @Test
    void shouldReturnNotFound_whenDeleteOrderDoesNotExist() throws Exception {
        doThrow(new ResourceNotFoundException("Order not found")).when(orderService).delete(99L);
        mockMvc.perform(delete("/orders/99"))
            .andExpect(status().isNotFound());
    }
}