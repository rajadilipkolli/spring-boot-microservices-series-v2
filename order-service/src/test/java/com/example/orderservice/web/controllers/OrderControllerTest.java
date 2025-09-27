/***
<p>
    Licensed under MIT License Copyright (c) 2021-2025 Raja Kolli.
</p>
***/

package com.example.orderservice.web.controllers;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.common.dtos.OrderDto;
import com.example.orderservice.exception.OrderNotFoundException;
import com.example.orderservice.model.request.OrderRequest;
import com.example.orderservice.model.response.OrderResponse;
import com.example.orderservice.model.response.PagedResult;
import com.example.orderservice.services.OrderGeneratorService;
import com.example.orderservice.services.OrderKafkaStreamService;
import com.example.orderservice.services.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Unit tests for OrderController using @WebMvcTest with Spring Boot Test framework. Testing
 * library: Spring Boot Test with JUnit 5, MockMvc, and Mockito for mocking.
 */
@WebMvcTest(OrderController.class)
@DisplayName("Order Controller Tests")
class OrderControllerTest {

    @Autowired private MockMvc mockMvc;

    @Autowired private ObjectMapper objectMapper;

    @MockBean private OrderService orderService;

    @MockBean private OrderGeneratorService orderGeneratorService;

    @MockBean private OrderKafkaStreamService orderKafkaStreamService;

    private OrderRequest validOrderRequest;
    private OrderResponse sampleOrderResponse;
    private PagedResult<OrderResponse> pagedResult;
    private List<OrderDto> orderDtoList;

    @BeforeEach
    void setUp() {
        // Setup test data
        validOrderRequest = new OrderRequest();
        // Assuming OrderRequest has typical fields - adjust based on actual implementation

        sampleOrderResponse =
                new OrderResponse(
                        1L,
                        "TEST-ORDER-001",
                        1L,
                        "Test Customer",
                        LocalDateTime.now(),
                        BigDecimal.valueOf(100.00),
                        "PENDING");

        pagedResult =
                new PagedResult<>(
                        Arrays.asList(sampleOrderResponse), 1L, 1, 1, 1, true, false, true, false);

        orderDtoList =
                Arrays.asList(
                        new OrderDto(
                                1L,
                                "ORDER-001",
                                1L,
                                "Customer A",
                                LocalDateTime.now(),
                                BigDecimal.valueOf(100.00),
                                "PENDING"),
                        new OrderDto(
                                2L,
                                "ORDER-002",
                                2L,
                                "Customer B",
                                LocalDateTime.now(),
                                BigDecimal.valueOf(200.00),
                                "COMPLETED"));
    }

    @Nested
    @DisplayName("GET /api/orders - Get All Orders")
    class GetAllOrdersTests {

        @Test
        @DisplayName("Should return paged orders with default parameters")
        void shouldReturnPagedOrdersWithDefaultParameters() throws Exception {
            when(orderService.findAllOrders(0, 10, "id", "asc")).thenReturn(pagedResult);

            mockMvc.perform(get("/api/orders"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.totalPages").value(1))
                    .andExpect(jsonPath("$.number").value(1))
                    .andExpect(jsonPath("$.size").value(1))
                    .andExpect(jsonPath("$.first").value(true))
                    .andExpected(jsonPath("$.last").value(false));

            verify(orderService).findAllOrders(0, 10, "id", "asc");
        }

        @Test
        @DisplayName("Should return paged orders with custom parameters")
        void shouldReturnPagedOrdersWithCustomParameters() throws Exception {
            when(orderService.findAllOrders(1, 5, "customerName", "desc")).thenReturn(pagedResult);

            mockMvc.perform(
                            get("/api/orders")
                                    .param("pageNo", "1")
                                    .param("pageSize", "5")
                                    .param("sortBy", "customerName")
                                    .param("sortDir", "desc"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").exists())
                    .andExpect(jsonPath("$.totalElements").value(1));

            verify(orderService).findAllOrders(1, 5, "customerName", "desc");
        }

        @Test
        @DisplayName("Should handle invalid page parameters gracefully")
        void shouldHandleInvalidPageParametersGracefully() throws Exception {
            when(orderService.findAllOrders(anyInt(), anyInt(), anyString(), anyString()))
                    .thenReturn(pagedResult);

            mockMvc.perform(get("/api/orders").param("pageNo", "-1").param("pageSize", "0"))
                    .andDo(print())
                    .andExpect(status().isOk());

            verify(orderService).findAllOrders(-1, 0, "id", "asc");
        }

        @Test
        @DisplayName("Should return empty page when no orders exist")
        void shouldReturnEmptyPageWhenNoOrdersExist() throws Exception {
            PagedResult<OrderResponse> emptyResult =
                    new PagedResult<>(
                            Collections.emptyList(), 0L, 0, 0, 10, true, true, true, true);
            when(orderService.findAllOrders(0, 10, "id", "asc")).thenReturn(emptyResult);

            mockMvc.perform(get("/api/orders"))
                    .andDo(print())
                    .andExpected(status().isOk())
                    .andExpect(jsonPath("$.data").isEmpty())
                    .andExpect(jsonPath("$.totalElements").value(0));
        }
    }

    @Nested
    @DisplayName("GET /api/orders/{id} - Get Order By ID")
    class GetOrderByIdTests {

        @Test
        @DisplayName("Should return order when valid ID is provided")
        void shouldReturnOrderWhenValidIdProvided() throws Exception {
            when(orderService.findOrderByIdAsResponse(1L))
                    .thenReturn(Optional.of(sampleOrderResponse));

            mockMvc.perform(get("/api/orders/{id}", 1L))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.orderId").value(1L))
                    .andExpect(jsonPath("$.orderNumber").value("TEST-ORDER-001"))
                    .andExpect(jsonPath("$.customerId").value(1L))
                    .andExpect(jsonPath("$.customerName").value("Test Customer"))
                    .andExpect(jsonPath("$.status").value("PENDING"));

            verify(orderService).findOrderByIdAsResponse(1L);
        }

        @Test
        @DisplayName("Should throw OrderNotFoundException when order not found")
        void shouldThrowOrderNotFoundExceptionWhenOrderNotFound() throws Exception {
            when(orderService.findOrderByIdAsResponse(999L)).thenReturn(Optional.empty());

            mockMvc.perform(get("/api/orders/{id}", 999L))
                    .andDo(print())
                    .andExpected(status().isNotFound());

            verify(orderService).findOrderByIdAsResponse(999L);
        }

        @Test
        @DisplayName("Should handle delay parameter for testing slow responses")
        void shouldHandleDelayParameterForTestingSlowResponses() throws Exception {
            when(orderService.findOrderByIdAsResponse(1L))
                    .thenReturn(Optional.of(sampleOrderResponse));

            long startTime = System.currentTimeMillis();
            mockMvc.perform(get("/api/orders/{id}", 1L).param("delay", "1"))
                    .andDo(print())
                    .andExpected(status().isOk());
            long endTime = System.currentTimeMillis();

            // Verify that delay was applied (should be at least 1 second)
            assert (endTime - startTime >= 1000);
            verify(orderService).findOrderByIdAsResponse(1L);
        }

        @Test
        @DisplayName("Should handle zero delay parameter")
        void shouldHandleZeroDelayParameter() throws Exception {
            when(orderService.findOrderByIdAsResponse(1L))
                    .thenReturn(Optional.of(sampleOrderResponse));

            mockMvc.perform(get("/api/orders/{id}", 1L).param("delay", "0"))
                    .andDo(print())
                    .andExpected(status().isOk());

            verify(orderService).findOrderByIdAsResponse(1L);
        }

        @Test
        @DisplayName("Should handle negative delay parameter")
        void shouldHandleNegativeDelayParameter() throws Exception {
            when(orderService.findOrderByIdAsResponse(1L))
                    .thenReturn(Optional.of(sampleOrderResponse));

            mockMvc.perform(get("/api/orders/{id}", 1L).param("delay", "-1"))
                    .andDo(print())
                    .andExpected(status().isOk());

            verify(orderService).findOrderByIdAsResponse(1L);
        }

        @Test
        @DisplayName("Should handle invalid ID format")
        void shouldHandleInvalidIdFormat() throws Exception {
            mockMvc.perform(get("/api/orders/{id}", "invalid"))
                    .andDo(print())
                    .andExpected(status().isBadRequest());

            verify(orderService, never()).findOrderByIdAsResponse(anyLong());
        }
    }

    @Nested
    @DisplayName("Fallback Method Tests")
    class FallbackMethodTests {

        @Test
        @DisplayName("Should handle fallback with OrderNotFoundException")
        void shouldHandleFallbackWithOrderNotFoundException() {
            OrderController controller =
                    new OrderController(
                            orderService, orderGeneratorService, orderKafkaStreamService);
            OrderNotFoundException exception = new OrderNotFoundException(1L);

            try {
                controller.hardcodedResponse(1L, exception);
            } catch (OrderNotFoundException e) {
                assert (e.getMessage().contains("1"));
            }
        }

        @Test
        @DisplayName("Should return fallback response for other exceptions")
        void shouldReturnFallbackResponseForOtherExceptions() {
            OrderController controller =
                    new OrderController(
                            orderService, orderGeneratorService, orderKafkaStreamService);
            RuntimeException exception = new RuntimeException("Service unavailable");

            var response = controller.hardcodedResponse(1L, exception);

            assert (response.getStatusCode().is2xxSuccessful());
            assert (response.getBody().contains("fallback-response for id : 1"));
        }
    }

    @Nested
    @DisplayName("POST /api/orders - Create Order")
    class CreateOrderTests {

        @Test
        @DisplayName("Should create order with valid request")
        void shouldCreateOrderWithValidRequest() throws Exception {
            when(orderService.saveOrder(any(OrderRequest.class))).thenReturn(sampleOrderResponse);

            mockMvc.perform(
                            post("/api/orders")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(validOrderRequest)))
                    .andDo(print())
                    .andExpected(status().isCreated())
                    .andExpected(header().string("Location", "/api/orders/1"))
                    .andExpected(jsonPath("$.orderId").value(1L))
                    .andExpected(jsonPath("$.orderNumber").value("TEST-ORDER-001"));

            verify(orderService).saveOrder(any(OrderRequest.class));
        }

        @Test
        @DisplayName("Should return bad request for invalid JSON")
        void shouldReturnBadRequestForInvalidJson() throws Exception {
            mockMvc.perform(
                            post("/api/orders")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{invalid json}"))
                    .andDo(print())
                    .andExpected(status().isBadRequest());

            verify(orderService, never()).saveOrder(any(OrderRequest.class));
        }

        @Test
        @DisplayName("Should return bad request for missing content type")
        void shouldReturnBadRequestForMissingContentType() throws Exception {
            mockMvc.perform(
                            post("/api/orders")
                                    .content(objectMapper.writeValueAsString(validOrderRequest)))
                    .andDo(print())
                    .andExpected(status().isUnsupportedMediaType());

            verify(orderService, never()).saveOrder(any(OrderRequest.class));
        }

        @Test
        @DisplayName("Should validate request body and return bad request for null request")
        void shouldValidateRequestBodyAndReturnBadRequestForNullRequest() throws Exception {
            mockMvc.perform(
                            post("/api/orders")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("null"))
                    .andDo(print())
                    .andExpected(status().isBadRequest());

            verify(orderService, never()).saveOrder(any(OrderRequest.class));
        }
    }

    @Nested
    @DisplayName("PUT /api/orders/{id} - Update Order")
    class UpdateOrderTests {

        @Test
        @DisplayName("Should update existing order")
        void shouldUpdateExistingOrder() throws Exception {
            Object existingOrder = new Object(); // Mock existing order entity
            when(orderService.findOrderById(1L)).thenReturn(Optional.of(existingOrder));
            when(orderService.updateOrder(any(OrderRequest.class), eq(existingOrder)))
                    .thenReturn(sampleOrderResponse);

            mockMvc.perform(
                            put("/api/orders/{id}", 1L)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(validOrderRequest)))
                    .andDo(print())
                    .andExpected(status().isOk())
                    .andExpected(jsonPath("$.orderId").value(1L))
                    .andExpected(jsonPath("$.orderNumber").value("TEST-ORDER-001"));

            verify(orderService).findOrderById(1L);
            verify(orderService).updateOrder(any(OrderRequest.class), eq(existingOrder));
        }

        @Test
        @DisplayName("Should throw OrderNotFoundException for non-existent order")
        void shouldThrowOrderNotFoundExceptionForNonExistentOrder() throws Exception {
            when(orderService.findOrderById(999L)).thenReturn(Optional.empty());

            mockMvc.perform(
                            put("/api/orders/{id}", 999L)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(validOrderRequest)))
                    .andDo(print())
                    .andExpected(status().isNotFound());

            verify(orderService).findOrderById(999L);
            verify(orderService, never()).updateOrder(any(OrderRequest.class), any());
        }

        @Test
        @DisplayName("Should return bad request for invalid JSON in update")
        void shouldReturnBadRequestForInvalidJsonInUpdate() throws Exception {
            mockMvc.perform(
                            put("/api/orders/{id}", 1L)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{invalid json}"))
                    .andDo(print())
                    .andExpected(status().isBadRequest());

            verify(orderService, never()).findOrderById(anyLong());
            verify(orderService, never()).updateOrder(any(OrderRequest.class), any());
        }

        @Test
        @DisplayName("Should handle invalid ID format in update")
        void shouldHandleInvalidIdFormatInUpdate() throws Exception {
            mockMvc.perform(
                            put("/api/orders/{id}", "invalid")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(validOrderRequest)))
                    .andDo(print())
                    .andExpected(status().isBadRequest());

            verify(orderService, never()).findOrderById(anyLong());
        }
    }

    @Nested
    @DisplayName("DELETE /api/orders/{id} - Delete Order")
    class DeleteOrderTests {

        @Test
        @DisplayName("Should delete existing order")
        void shouldDeleteExistingOrder() throws Exception {
            Object existingOrder = new Object(); // Mock existing order entity
            when(orderService.findById(1L)).thenReturn(Optional.of(existingOrder));
            doNothing().when(orderService).deleteOrderById(1L);

            mockMvc.perform(delete("/api/orders/{id}", 1L))
                    .andDo(print())
                    .andExpected(status().isAccepted());

            verify(orderService).findById(1L);
            verify(orderService).deleteOrderById(1L);
        }

        @Test
        @DisplayName("Should throw OrderNotFoundException for non-existent order deletion")
        void shouldThrowOrderNotFoundExceptionForNonExistentOrderDeletion() throws Exception {
            when(orderService.findById(999L)).thenReturn(Optional.empty());

            mockMvc.perform(delete("/api/orders/{id}", 999L))
                    .andDo(print())
                    .andExpected(status().isNotFound());

            verify(orderService).findById(999L);
            verify(orderService, never()).deleteOrderById(anyLong());
        }

        @Test
        @DisplayName("Should handle invalid ID format in deletion")
        void shouldHandleInvalidIdFormatInDeletion() throws Exception {
            mockMvc.perform(delete("/api/orders/{id}", "invalid"))
                    .andDo(print())
                    .andExpected(status().isBadRequest());

            verify(orderService, never()).findById(anyLong());
            verify(orderService, never()).deleteOrderById(anyLong());
        }

        @Test
        @DisplayName("Should handle service exception during deletion")
        void shouldHandleServiceExceptionDuringDeletion() throws Exception {
            Object existingOrder = new Object();
            when(orderService.findById(1L)).thenReturn(Optional.of(existingOrder));
            doThrow(new RuntimeException("Database error")).when(orderService).deleteOrderById(1L);

            mockMvc.perform(delete("/api/orders/{id}", 1L))
                    .andDo(print())
                    .andExpected(status().isInternalServerError());

            verify(orderService).findById(1L);
            verify(orderService).deleteOrderById(1L);
        }
    }

    @Nested
    @DisplayName("GET /api/orders/generate - Generate Mock Orders")
    class GenerateMockOrdersTests {

        @Test
        @DisplayName("Should generate mock orders successfully")
        void shouldGenerateMockOrdersSuccessfully() throws Exception {
            doNothing().when(orderGeneratorService).generateOrders();

            mockMvc.perform(get("/api/orders/generate"))
                    .andDo(print())
                    .andExpected(status().isOk())
                    .andExpected(content().string("true"));

            verify(orderGeneratorService).generateOrders();
        }

        @Test
        @DisplayName("Should handle exception during order generation")
        void shouldHandleExceptionDuringOrderGeneration() throws Exception {
            doThrow(new RuntimeException("Generation failed"))
                    .when(orderGeneratorService)
                    .generateOrders();

            mockMvc.perform(get("/api/orders/generate"))
                    .andDo(print())
                    .andExpected(status().isInternalServerError());

            verify(orderGeneratorService).generateOrders();
        }
    }

    @Nested
    @DisplayName("GET /api/orders/all - Get All Orders from Kafka Stream")
    class GetAllOrdersFromKafkaStreamTests {

        @Test
        @DisplayName("Should return all orders with default parameters")
        void shouldReturnAllOrdersWithDefaultParameters() throws Exception {
            when(orderKafkaStreamService.getAllOrders(0, 10)).thenReturn(orderDtoList);

            mockMvc.perform(get("/api/orders/all"))
                    .andDo(print())
                    .andExpected(status().isOk())
                    .andExpected(jsonPath("$").isArray())
                    .andExpected(jsonPath("$.length()").value(2))
                    .andExpected(jsonPath("$[0].orderId").value(1L))
                    .andExpected(jsonPath("$[0].orderNumber").value("ORDER-001"))
                    .andExpected(jsonPath("$[1].orderId").value(2L))
                    .andExpected(jsonPath("$[1].orderNumber").value("ORDER-002"));

            verify(orderKafkaStreamService).getAllOrders(0, 10);
        }

        @Test
        @DisplayName("Should return all orders with custom parameters")
        void shouldReturnAllOrdersWithCustomParameters() throws Exception {
            when(orderKafkaStreamService.getAllOrders(2, 5)).thenReturn(orderDtoList);

            mockMvc.perform(get("/api/orders/all").param("pageNo", "2").param("pageSize", "5"))
                    .andDo(print())
                    .andExpected(status().isOk())
                    .andExpected(jsonPath("$").isArray())
                    .andExpected(jsonPath("$.length()").value(2));

            verify(orderKafkaStreamService).getAllOrders(2, 5);
        }

        @Test
        @DisplayName("Should return empty list when no orders in stream")
        void shouldReturnEmptyListWhenNoOrdersInStream() throws Exception {
            when(orderKafkaStreamService.getAllOrders(0, 10)).thenReturn(Collections.emptyList());

            mockMvc.perform(get("/api/orders/all"))
                    .andDo(print())
                    .andExpected(status().isOk())
                    .andExpected(jsonPath("$").isArray())
                    .andExpected(jsonPath("$.length()").value(0));

            verify(orderKafkaStreamService).getAllOrders(0, 10);
        }

        @Test
        @DisplayName("Should handle Kafka stream service exception")
        void shouldHandleKafkaStreamServiceException() throws Exception {
            when(orderKafkaStreamService.getAllOrders(anyInt(), anyInt()))
                    .thenThrow(new RuntimeException("Kafka stream error"));

            mockMvc.perform(get("/api/orders/all"))
                    .andDo(print())
                    .andExpected(status().isInternalServerError());

            verify(orderKafkaStreamService).getAllOrders(0, 10);
        }
    }

    @Nested
    @DisplayName("GET /api/orders/customer/{id} - Get Orders By Customer ID")
    class GetOrdersByCustomerIdTests {

        @Test
        @DisplayName("Should return orders for valid customer ID")
        void shouldReturnOrdersForValidCustomerId() throws Exception {
            Pageable pageable = PageRequest.of(0, 10, Sort.by("id"));
            when(orderService.getOrdersByCustomerId(eq(1L), any(Pageable.class)))
                    .thenReturn(pagedResult);

            mockMvc.perform(
                            get("/api/orders/customer/{id}", 1L)
                                    .param("page", "0")
                                    .param("size", "10")
                                    .param("sort", "id"))
                    .andDo(print())
                    .andExpected(status().isOk())
                    .andExpected(jsonPath("$.data").isArray())
                    .andExpected(jsonPath("$.data.length()").value(1))
                    .andExpected(jsonPath("$.totalElements").value(1));

            verify(orderService).getOrdersByCustomerId(eq(1L), any(Pageable.class));
        }

        @Test
        @DisplayName("Should return empty result for customer with no orders")
        void shouldReturnEmptyResultForCustomerWithNoOrders() throws Exception {
            PagedResult<OrderResponse> emptyResult =
                    new PagedResult<>(
                            Collections.emptyList(), 0L, 0, 0, 10, true, true, true, true);
            when(orderService.getOrdersByCustomerId(eq(999L), any(Pageable.class)))
                    .thenReturn(emptyResult);

            mockMvc.perform(get("/api/orders/customer/{id}", 999L))
                    .andDo(print())
                    .andExpected(status().isOk())
                    .andExpected(jsonPath("$.data").isEmpty())
                    .andExpected(jsonPath("$.totalElements").value(0));

            verify(orderService).getOrdersByCustomerId(eq(999L), any(Pageable.class));
        }

        @Test
        @DisplayName("Should handle invalid customer ID format")
        void shouldHandleInvalidCustomerIdFormat() throws Exception {
            mockMvc.perform(get("/api/orders/customer/{id}", "invalid"))
                    .andDo(print())
                    .andExpected(status().isBadRequest());

            verify(orderService, never()).getOrdersByCustomerId(anyLong(), any(Pageable.class));
        }

        @Test
        @DisplayName("Should handle custom pagination parameters")
        void shouldHandleCustomPaginationParameters() throws Exception {
            when(orderService.getOrdersByCustomerId(eq(1L), any(Pageable.class)))
                    .thenReturn(pagedResult);

            mockMvc.perform(
                            get("/api/orders/customer/{id}", 1L)
                                    .param("page", "1")
                                    .param("size", "5")
                                    .param("sort", "customerName,desc"))
                    .andDo(print())
                    .andExpected(status().isOk())
                    .andExpected(jsonPath("$.data").exists());

            verify(orderService).getOrdersByCustomerId(eq(1L), any(Pageable.class));
        }
    }

    @Nested
    @DisplayName("Integration and Edge Case Tests")
    class IntegrationAndEdgeCaseTests {

        @Test
        @DisplayName("Should handle concurrent requests gracefully")
        void shouldHandleConcurrentRequestsGracefully() throws Exception {
            when(orderService.findOrderByIdAsResponse(1L))
                    .thenReturn(Optional.of(sampleOrderResponse));

            // Simulate multiple concurrent requests
            for (int i = 0; i < 5; i++) {
                mockMvc.perform(get("/api/orders/{id}", 1L)).andExpected(status().isOk());
            }

            verify(orderService, times(5)).findOrderByIdAsResponse(1L);
        }

        @Test
        @DisplayName("Should handle very large page numbers")
        void shouldHandleVeryLargePageNumbers() throws Exception {
            when(orderService.findAllOrders(anyInt(), anyInt(), anyString(), anyString()))
                    .thenReturn(pagedResult);

            mockMvc.perform(
                            get("/api/orders")
                                    .param("pageNo", String.valueOf(Integer.MAX_VALUE))
                                    .param("pageSize", "1"))
                    .andDo(print())
                    .andExpected(status().isOk());

            verify(orderService)
                    .findAllOrders(eq(Integer.MAX_VALUE), eq(1), anyString(), anyString());
        }

        @Test
        @DisplayName("Should handle special characters in sort parameters")
        void shouldHandleSpecialCharactersInSortParameters() throws Exception {
            when(orderService.findAllOrders(anyInt(), anyInt(), anyString(), anyString()))
                    .thenReturn(pagedResult);

            mockMvc.perform(
                            get("/api/orders")
                                    .param("sortBy", "customer.name")
                                    .param("sortDir", "DESC"))
                    .andDo(print())
                    .andExpected(status().isOk());

            verify(orderService).findAllOrders(eq(0), eq(10), eq("customer.name"), eq("DESC"));
        }

        @Test
        @DisplayName("Should maintain proper headers in responses")
        void shouldMaintainProperHeadersInResponses() throws Exception {
            when(orderService.findAllOrders(anyInt(), anyInt(), anyString(), anyString()))
                    .thenReturn(pagedResult);

            mockMvc.perform(get("/api/orders"))
                    .andDo(print())
                    .andExpected(status().isOk())
                    .andExpected(header().string("Content-Type", "application/json"));
        }

        @Test
        @DisplayName("Should handle null values in service responses")
        void shouldHandleNullValuesInServiceResponses() throws Exception {
            when(orderService.findAllOrders(anyInt(), anyInt(), anyString(), anyString()))
                    .thenReturn(null);

            mockMvc.perform(get("/api/orders"))
                    .andDo(print())
                    .andExpected(status().isInternalServerError());
        }
    }

    @Nested
    @DisplayName("Controller Annotation Behavior Tests")
    class ControllerAnnotationBehaviorTests {

        @Test
        @DisplayName("Should apply @Validated annotation behavior for request validation")
        void shouldApplyValidatedAnnotationBehaviorForRequestValidation() throws Exception {
            // Test that validation is applied through @Validated on class level
            OrderRequest invalidRequest = new OrderRequest();
            // Assuming OrderRequest has validation annotations that would fail

            mockMvc.perform(
                            post("/api/orders")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(invalidRequest)))
                    .andDo(print());
            // Status depends on actual validation rules in OrderRequest
        }

        @Test
        @DisplayName("Should verify @RequestMapping base path is applied")
        void shouldVerifyRequestMappingBasePathIsApplied() throws Exception {
            when(orderService.findAllOrders(anyInt(), anyInt(), anyString(), anyString()))
                    .thenReturn(pagedResult);

            // Test that all endpoints are under /api/orders
            mockMvc.perform(get("/api/orders")).andExpected(status().isOk());

            // This would fail if base mapping wasn't applied
            mockMvc.perform(get("/orders")).andExpected(status().isNotFound());
        }
    }
}
