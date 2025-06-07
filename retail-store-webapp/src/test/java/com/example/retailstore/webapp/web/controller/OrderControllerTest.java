package com.example.retailstore.webapp.web.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.example.retailstore.webapp.clients.PagedResult;
import com.example.retailstore.webapp.clients.customer.CustomerRequest;
import com.example.retailstore.webapp.clients.customer.CustomerResponse;
import com.example.retailstore.webapp.clients.customer.CustomerServiceClient;
import com.example.retailstore.webapp.clients.order.Address;
import com.example.retailstore.webapp.clients.order.CreateOrderRequest;
import com.example.retailstore.webapp.clients.order.OrderConfirmationDTO;
import com.example.retailstore.webapp.clients.order.OrderItemRequest;
import com.example.retailstore.webapp.clients.order.OrderItemResponse;
import com.example.retailstore.webapp.clients.order.OrderRequestExternal;
import com.example.retailstore.webapp.clients.order.OrderResponse;
import com.example.retailstore.webapp.clients.order.OrderServiceClient;
import com.example.retailstore.webapp.config.TestSecurityConfig;
import com.example.retailstore.webapp.services.SecurityHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.HttpClientErrorException;

@WebMvcTest(controllers = OrderController.class)
@Import(TestSecurityConfig.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderServiceClient orderServiceClient;

    @MockitoBean
    private CustomerServiceClient customerServiceClient;

    @MockitoBean
    private SecurityHelper securityHelper;

    @Autowired
    private ObjectMapper objectMapper;

    private List<OrderResponse> orderResponseList;
    private PagedResult<OrderResponse> pagedResult;
    private CustomerResponse customerResponse;
    private OrderConfirmationDTO orderConfirmation;

    @BeforeEach
    void setUp() {
        // Set up test data for orders
        Address address = new Address("123 Test St", "Apt 4", "Test City", "Test State", "12345", "Test Country");

        List<OrderItemResponse> orderItems = List.of(
                new OrderItemResponse(1L, "PROD-1", 2, new BigDecimal("10.99"), new BigDecimal("21.98")),
                new OrderItemResponse(2L, "PROD-2", 1, new BigDecimal("20.99"), new BigDecimal("20.99")));

        orderResponseList = List.of(
                new OrderResponse(1L, 1L, "NEW", "", address, LocalDateTime.now(), new BigDecimal("42.97"), orderItems),
                new OrderResponse(
                        2L,
                        1L,
                        "DELIVERED",
                        "",
                        address,
                        LocalDateTime.now().minusDays(1),
                        new BigDecimal("30.99"),
                        List.of(new OrderItemResponse(
                                3L, "PROD-3", 1, new BigDecimal("30.99"), new BigDecimal("30.99")))));

        pagedResult = new PagedResult<>(
                orderResponseList,
                2L, // Total elements
                0, // Page number
                1, // Total pages
                true, // Is first
                true, // Is last
                false, // Has next
                false // Has previous
                );

        // Set up customer response
        customerResponse = new CustomerResponse(1L, "Test User", "test@example.com", "1234567890", "123 Test St", 5000);

        // Set up order confirmation
        orderConfirmation = new OrderConfirmationDTO(123L, 1L, "NEW");

        // Mock security helper
        when(securityHelper.getAccessToken()).thenReturn("test-token");
        when(securityHelper.getLoggedInUserEmail()).thenReturn("test@example.com");
    }

    @Test
    @WithMockUser
    void cart_shouldRenderCartPageAndProcessTemplateWithoutErrors() throws Exception {
        // Use MockMvc to render the template and check for successful processing
        mockMvc.perform(get("/cart").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("cart"))
                .andExpect(result -> {
                    // If we get here without exceptions, template processing was successful
                    String contentAsString = result.getResponse().getContentAsString();
                    // Check for expected content to ensure template was actually rendered
                    assert (contentAsString.contains("Your cart"));
                });
    }

    @Test
    @WithMockUser
    void showOrderDetails_shouldRenderOrderDetailsPage() throws Exception {
        String orderNumber = "ORDER-123";

        mockMvc.perform(get("/orders/{orderNumber}", orderNumber).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("order_details"))
                .andExpect(model().attribute("orderNumber", orderNumber));
    }

    @Test
    @WithMockUser
    void getOrder_shouldReturnOrderDetails() throws Exception {
        String orderNumber = "ORDER-123";
        OrderResponse orderResponse = orderResponseList.get(0);

        when(orderServiceClient.getOrder(anyMap(), anyString())).thenReturn(orderResponse);

        mockMvc.perform(get("/api/orders/{orderNumber}", orderNumber).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId", is(1)))
                .andExpect(jsonPath("$.customerId", is(1)))
                .andExpect(jsonPath("$.status", is("NEW")))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].productId", is("PROD-1")))
                .andExpect(jsonPath("$.items[0].quantity", is(2)));
    }

    @Test
    @WithMockUser
    void showOrders_shouldRenderOrdersPage() throws Exception {
        mockMvc.perform(get("/orders").with(csrf())).andExpect(status().isOk()).andExpect(view().name("orders"));
    }

    @Test
    @WithMockUser
    void getOrders_shouldReturnPagedOrders() throws Exception {
        when(orderServiceClient.getOrders(anyMap())).thenReturn(pagedResult);

        mockMvc.perform(get("/api/orders").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].orderId", is(1)))
                .andExpect(jsonPath("$.data[0].customerId", is(1)))
                .andExpect(jsonPath("$.data[0].status", is("NEW")))
                .andExpect(jsonPath("$.data[0].items", hasSize(2)))
                .andExpect(jsonPath("$.data[1].orderId", is(2)))
                .andExpect(jsonPath("$.data[1].status", is("DELIVERED")))
                .andExpect(jsonPath("$.totalElements", is(2)));
    }

    @Test
    @WithMockUser
    void createOrder_shouldCreateAndReturnOrderConfirmation() throws Exception {
        // Create test request objects
        CustomerRequest customerRequest =
                new CustomerRequest("Test User", "original@example.com", "1234567890", "Test Address", 5000);

        List<OrderItemRequest> items = List.of(
                new OrderItemRequest("PROD-1", 2, new BigDecimal("10.99")),
                new OrderItemRequest("PROD-2", 1, new BigDecimal("20.99")));

        Address address = new Address("123 Test St", "Apt 4", "Test City", "Test State", "12345", "Test Country");

        CreateOrderRequest createOrderRequest = new CreateOrderRequest(items, customerRequest, address);

        // Mock client responses
        when(customerServiceClient.getOrCreateCustomer(any(CustomerRequest.class)))
                .thenReturn(customerResponse);
        when(orderServiceClient.createOrder(anyMap(), any(OrderRequestExternal.class)))
                .thenReturn(orderConfirmation);

        // Perform test
        mockMvc.perform(post("/api/orders")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createOrderRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId", is(123)))
                .andExpect(jsonPath("$.customerId", is(1)))
                .andExpect(jsonPath("$.status", is("NEW")));
    }

    @Test
    @WithMockUser
    void getOrder_shouldHandleErrorWhenServiceFails() throws Exception {
        String orderNumber = "ORDER-123";

        when(orderServiceClient.getOrder(anyMap(), anyString()))
                .thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND));

        mockMvc.perform(get("/api/orders/{orderNumber}", orderNumber).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    void getOrders_shouldHandleErrorWhenServiceFails() throws Exception {
        when(orderServiceClient.getOrders(anyMap()))
                .thenThrow(new HttpClientErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

        mockMvc.perform(get("/api/orders").with(csrf())).andExpect(status().isInternalServerError());
    }

    @Test
    @WithMockUser
    void createOrder_shouldHandleErrorWhenCustomerServiceFails() throws Exception {
        // Create test request objects
        CustomerRequest customerRequest =
                new CustomerRequest("Test User", "original@example.com", "1234567890", "Test Address", 5000);
        List<OrderItemRequest> items = List.of(new OrderItemRequest("PROD-1", 2, new BigDecimal("10.99")));
        Address address = new Address("123 Test St", "Apt 4", "Test City", "Test State", "12345", "Test Country");
        CreateOrderRequest createOrderRequest = new CreateOrderRequest(items, customerRequest, address);

        // Mock client responses for failure
        when(customerServiceClient.getOrCreateCustomer(any(CustomerRequest.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST));

        mockMvc.perform(post("/api/orders")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createOrderRequest)))
                .andExpect(status().isBadRequest());
    }
}
