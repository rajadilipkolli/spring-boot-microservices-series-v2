package com.example.retailstore.webapp.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.common.dto.PagedResult;
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
import com.example.retailstore.webapp.exception.InvalidRequestException;
import com.example.retailstore.webapp.exception.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderServiceClient orderServiceClient;

    @Mock
    private CustomerServiceClient customerServiceClient;

    @Mock
    private SecurityHelper securityHelper;

    private OrderService orderService;

    private CustomerResponse customerResponse;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderServiceClient, customerServiceClient, securityHelper);

        customerResponse = new CustomerResponse(
                1L,
                "Test User",
                "test@example.com",
                "1234567890",
                "123 Test St",
                null,
                "Test City",
                "TS",
                "12345",
                "US",
                BigDecimal.valueOf(5000));
    }

    @Test
    void getOrder_shouldReturnEnrichedOrderWhenOwnershipMatches() {
        String orderNumber = "ORD-001";
        Address address = new Address("123 Test St", "Apt 4", "Test City", "TS", "12345", "US");
        List<OrderItemResponse> items =
                List.of(new OrderItemResponse(1L, "PROD-1", 2, BigDecimal.TEN, BigDecimal.valueOf(20)));
        OrderResponse orderResponse =
                new OrderResponse(1L, 1L, "NEW", "", address, LocalDateTime.now(), BigDecimal.valueOf(20), items);

        when(orderServiceClient.getOrder(orderNumber)).thenReturn(orderResponse);
        when(securityHelper.getLoggedInUserEmail()).thenReturn("test@example.com");
        when(customerServiceClient.getCustomerByEmail("test@example.com")).thenReturn(customerResponse);

        OrderResponse result = orderService.getOrder(orderNumber);

        assertThat(result).isNotNull();
        assertThat(result.getOrderId()).isEqualTo(1L);
        assertThat(result.getCustomer()).isEqualTo(customerResponse);
    }

    @Test
    void getOrder_shouldThrowResourceNotFoundExceptionWhenCustomerIdDoesNotMatch() {
        String orderNumber = "ORD-001";
        Address address = new Address("123 Test St", "Apt 4", "Test City", "TS", "12345", "US");
        OrderResponse orderResponse =
                new OrderResponse(1L, 999L, "NEW", "", address, LocalDateTime.now(), BigDecimal.TEN, List.of());

        when(orderServiceClient.getOrder(orderNumber)).thenReturn(orderResponse);
        when(securityHelper.getLoggedInUserEmail()).thenReturn("test@example.com");
        when(customerServiceClient.getCustomerByEmail("test@example.com")).thenReturn(customerResponse);

        assertThatThrownBy(() -> orderService.getOrder(orderNumber))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Order not found with orderNumber : 'ORD-001'");
    }

    @Test
    void getOrder_shouldThrowResourceNotFoundExceptionWhenDownstreamClientFails() {
        String orderNumber = "ORD-FAIL";
        when(orderServiceClient.getOrder(orderNumber)).thenThrow(new RuntimeException("Downstream error"));

        assertThatThrownBy(() -> orderService.getOrder(orderNumber))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Order not found with orderNumber : 'ORD-FAIL'");
    }

    @Test
    void getOrders_shouldReturnPagedOrdersForLoggedInCustomer() {
        when(securityHelper.getLoggedInUserEmail()).thenReturn("test@example.com");
        when(customerServiceClient.getCustomerByEmail("test@example.com")).thenReturn(customerResponse);

        PagedResult<OrderResponse> pagedResult = new PagedResult<>(List.of(), 0L, 0, 0, true, true, false, false);
        when(orderServiceClient.getOrdersByCustomer(1L)).thenReturn(pagedResult);

        PagedResult<OrderResponse> result = orderService.getOrders();

        assertThat(result).isNotNull();
        verify(orderServiceClient).getOrdersByCustomer(1L);
    }

    @Test
    void createOrder_shouldCreateAndReturnOrderConfirmation() {
        CustomerRequest customerRequest = new CustomerRequest(
                "Test User",
                "test@example.com",
                "1234567890",
                "123 Test St",
                null,
                null,
                null,
                null,
                null,
                BigDecimal.valueOf(5000));
        List<OrderItemRequest> items = List.of(new OrderItemRequest("PROD-1", 2, BigDecimal.TEN));
        Address address = new Address("123 Test St", "Apt 4", "Test City", "TS", "12345", "US");
        CreateOrderRequest request = new CreateOrderRequest(items, customerRequest, address);

        OrderConfirmationDTO confirmation = new OrderConfirmationDTO(100L, 1L, "NEW");

        when(securityHelper.getLoggedInUserEmail()).thenReturn("test@example.com");
        when(customerServiceClient.getCustomerByEmail("test@example.com")).thenReturn(customerResponse);
        when(orderServiceClient.createOrder(any(OrderRequestExternal.class))).thenReturn(confirmation);

        OrderConfirmationDTO result = orderService.createOrder(request);

        assertThat(result).isNotNull();
        assertThat(result.orderId()).isEqualTo(100L);

        ArgumentCaptor<OrderRequestExternal> captor = ArgumentCaptor.forClass(OrderRequestExternal.class);
        verify(orderServiceClient).createOrder(captor.capture());
        assertThat(captor.getValue().customerId()).isEqualTo(1L);
    }

    @Test
    void createOrder_shouldThrowInvalidRequestExceptionWhenCreationFails() {
        CreateOrderRequest request = new CreateOrderRequest(List.of(), null, null);

        when(securityHelper.getLoggedInUserEmail()).thenReturn("test@example.com");
        when(customerServiceClient.getCustomerByEmail("test@example.com"))
                .thenThrow(new RuntimeException("Customer service down"));

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Failed to create order. Please try again later.");
    }
}
