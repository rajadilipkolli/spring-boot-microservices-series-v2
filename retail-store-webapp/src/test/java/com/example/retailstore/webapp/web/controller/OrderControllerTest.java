package com.example.retailstore.webapp.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.retailstore.webapp.clients.customer.CustomerRequest;
import com.example.retailstore.webapp.clients.customer.CustomerResponse;
import com.example.retailstore.webapp.clients.customer.CustomerServiceClient;
import com.example.retailstore.webapp.clients.order.Address;
import com.example.retailstore.webapp.clients.order.CreateOrderRequest;
import com.example.retailstore.webapp.clients.order.OrderConfirmationDTO;
import com.example.retailstore.webapp.clients.order.OrderResponse;
import com.example.retailstore.webapp.clients.order.OrderServiceClient;
import com.example.retailstore.webapp.config.TestSecurityConfig;
import com.example.retailstore.webapp.services.SecurityHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClientException;

@WebMvcTest(OrderController.class)
@Import(TestSecurityConfig.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderServiceClient orderServiceClient;

    @MockBean
    private CustomerServiceClient customerServiceClient;

    @MockBean
    private SecurityHelper securityHelper;

    @Test
    @WithMockUser
    void getOrder_shouldReturnOrderDetails() throws Exception {
        // Arrange
        String orderNumber = "ORDER-123";
        OrderResponse mockOrder = new OrderResponse(
                1L, 1L, "CREATED", "WEB", null, LocalDateTime.now(), BigDecimal.TEN, Collections.emptyList());

        when(orderServiceClient.getOrder(any(), eq(orderNumber))).thenReturn(mockOrder);

        // Act & Assert
        mockMvc.perform(get("/api/orders/{orderNumber}", orderNumber).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().json(objectMapper.writeValueAsString(mockOrder)));
    }

    @Test
    @WithMockUser
    void getOrder_shouldReturn404WhenOrderNotFound() throws Exception {
        // Arrange
        String orderNumber = "INVALID-ORDER";
        when(orderServiceClient.getOrder(any(), eq(orderNumber))).thenThrow(new RestClientException("Order not found"));

        // Act & Assert
        mockMvc.perform(get("/api/orders/{orderNumber}", orderNumber).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.detail").value("Order not found with orderNumber : 'INVALID-ORDER'"));
    }

    @Test
    @WithMockUser
    void createOrder_shouldCreateOrderSuccessfully() throws Exception {
        // Arrange
        String userEmail = "test@example.com";
        when(securityHelper.getLoggedInUserEmail()).thenReturn(userEmail);

        CustomerRequest customerRequest =
                new CustomerRequest("Test User", userEmail, "+1234567890", "123 Street", 10000);
        Address address = new Address("123 Street", "Line 2", "City", "State", "12345", "Country");
        CreateOrderRequest createOrderRequest =
                new CreateOrderRequest(Collections.emptyList(), customerRequest, address);

        CustomerResponse customerResponse =
                new CustomerResponse(1L, "Test User", userEmail, "+1234567890", "123 Street", 10000);
        when(customerServiceClient.getOrCreateCustomer(any())).thenReturn(customerResponse);

        OrderConfirmationDTO confirmation = new OrderConfirmationDTO(1L, 1L, "CREATED");
        when(orderServiceClient.createOrder(any(), any())).thenReturn(confirmation);

        // Act & Assert
        mockMvc.perform(post("/api/orders")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createOrderRequest)))
                .andExpect(status().isOk())
                .andExpect(content().json(objectMapper.writeValueAsString(confirmation)));
    }

    @Test
    @WithMockUser
    void createOrder_shouldReturn400WhenEmailNotAvailable() throws Exception {
        // Arrange
        when(securityHelper.getLoggedInUserEmail()).thenReturn(null);

        CustomerRequest customerRequest =
                new CustomerRequest("Test User", "test@example.com", "+1234567890", "123 Street", 10000);
        Address address = new Address("123 Street", "Line 2", "City", "State", "12345", "Country");
        CreateOrderRequest createOrderRequest =
                new CreateOrderRequest(Collections.emptyList(), customerRequest, address);

        // Act & Assert
        mockMvc.perform(post("/api/orders")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createOrderRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.detail").value("User email not available"));
    }

    @Test
    @WithMockUser
    void createOrder_shouldReturn400WhenServiceFails() throws Exception {
        // Arrange
        String userEmail = "test@example.com";
        when(securityHelper.getLoggedInUserEmail()).thenReturn(userEmail);

        CustomerRequest customerRequest =
                new CustomerRequest("Test User", userEmail, "+1234567890", "123 Street", 10000);
        Address address = new Address("123 Street", "Line 2", "City", "State", "12345", "Country");
        CreateOrderRequest createOrderRequest =
                new CreateOrderRequest(Collections.emptyList(), customerRequest, address);

        when(customerServiceClient.getOrCreateCustomer(any()))
                .thenThrow(new RestClientException("Service unavailable"));

        // Act & Assert
        mockMvc.perform(post("/api/orders")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createOrderRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.detail").value("Failed to create order: Service unavailable"));
    }
}
