/* Licensed under Apache-2.0 2023 */
package com.example.inventoryservice.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.example.common.dtos.OrderDto;
import com.example.common.dtos.OrderItemDto;
import com.example.inventoryservice.entities.Inventory;
import com.example.inventoryservice.repositories.InventoryRepository;
import com.example.inventoryservice.utils.AppConstants;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

@ExtendWith(MockitoExtension.class)
class InventoryOrderManageServiceTest {

    @Mock private InventoryRepository inventoryRepository;

    @Mock private KafkaTemplate<String, OrderDto> kafkaTemplate;

    @InjectMocks private InventoryOrderManageService inventoryOrderManageService;

    @Test
    void testReserve_AllProductsExist_OrderStatusIsNew_OrderIsAccepted() {
        // Arrange
        OrderDto orderDto = new OrderDto();
        orderDto.setOrderId(1L);
        orderDto.setStatus("NEW");

        List<OrderItemDto> orderItems = new ArrayList<>();
        orderItems.add(new OrderItemDto(1L, "product1", 10, BigDecimal.TEN));
        orderItems.add(new OrderItemDto(2L, "product2", 20, BigDecimal.TEN));

        orderDto.setItems(orderItems);

        given(inventoryRepository.findByProductCodeInAndQuantityAvailable(anyList()))
                .willReturn(
                        new ArrayList<>(
                                Arrays.asList(
                                        new Inventory(1L, "product1", 10, 0),
                                        new Inventory(2L, "product2", 10, 0))));

        // Act
        inventoryOrderManageService.reserve(orderDto);

        // Assert
        assertEquals("ACCEPT", orderDto.getStatus());
        verify(inventoryRepository, times(1)).saveAll(anyList());
        verify(kafkaTemplate, times(1))
                .send(
                        AppConstants.STOCK_ORDERS_TOPIC,
                        String.valueOf(orderDto.getOrderId()),
                        orderDto);
        verifyNoMoreInteractions(inventoryRepository, kafkaTemplate);
    }

    @Test
    void testReserve_NotAllProductsExist_OrderStatusIsNew_OrderIsNotProcessed() {
        // Arrange
        OrderDto orderDto = new OrderDto();
        orderDto.setOrderId(1L);
        orderDto.setStatus("NEW");

        List<OrderItemDto> orderItems = new ArrayList<>();
        orderItems.add(new OrderItemDto(1L, "product1", 10, BigDecimal.TEN));
        orderItems.add(new OrderItemDto(2L, "product2", 20, BigDecimal.TEN));
        orderDto.setItems(orderItems);

        given(inventoryRepository.findByProductCodeInAndQuantityAvailable(anyList()))
                .willReturn(new ArrayList<>(List.of(new Inventory(1L, "product1", 0, 0))));

        // Act
        inventoryOrderManageService.reserve(orderDto);

        // Assert
        assertEquals("REJECT", orderDto.getStatus());
        verify(inventoryRepository, times(1)).findByProductCodeInAndQuantityAvailable(anyList());
        verify(kafkaTemplate, times(1))
                .send(
                        AppConstants.STOCK_ORDERS_TOPIC,
                        String.valueOf(orderDto.getOrderId()),
                        orderDto);
        verifyNoMoreInteractions(inventoryRepository);
    }

    @Test
    void testReserve_OrderStatusIsNotNew_OrderIsRejected() {
        // Arrange
        OrderDto orderDto = new OrderDto();
        orderDto.setOrderId(1L);
        orderDto.setStatus("REJECT");

        List<OrderItemDto> orderItems = new ArrayList<>();
        orderItems.add(new OrderItemDto(1L, "product1", 10, BigDecimal.TEN));
        orderItems.add(new OrderItemDto(2L, "product2", 20, BigDecimal.TEN));

        orderDto.setItems(orderItems);

        // Act
        inventoryOrderManageService.reserve(orderDto);

        // Assert
        assertEquals("REJECT", orderDto.getStatus());
        verifyNoInteractions(kafkaTemplate, inventoryRepository);
    }

    @Test
    void testReserve() {
        // Arrange
        OrderDto orderDto = new OrderDto();
        orderDto.setOrderId(1L);
        orderDto.setStatus("NEW");
        List<OrderItemDto> orderItems = new ArrayList<>();
        orderItems.add(new OrderItemDto(1L, "product1", 10, BigDecimal.TEN));
        orderItems.add(new OrderItemDto(1L, "product2", 20, BigDecimal.TEN));
        orderDto.setItems(orderItems);

        List<Inventory> inventoryList = new ArrayList<>();
        inventoryList.add(new Inventory(1L, "product1", 0, 0));
        inventoryList.add(new Inventory(2L, "product2", 0, 0));

        given(inventoryRepository.findByProductCodeInAndQuantityAvailable(anyList()))
                .willReturn(inventoryList);

        // Act
        inventoryOrderManageService.reserve(orderDto);

        // Assert
        verify(inventoryRepository, times(1)).findByProductCodeInAndQuantityAvailable(anyList());
        verify(inventoryRepository, times(1)).saveAll(anyList());
        assertEquals("ACCEPT", orderDto.getStatus());
        verify(kafkaTemplate, times(1))
                .send(
                        AppConstants.STOCK_ORDERS_TOPIC,
                        String.valueOf(orderDto.getOrderId()),
                        orderDto);
        verifyNoMoreInteractions(inventoryRepository, kafkaTemplate);
    }

    @Test
    void testConfirm() {
        // Arrange
        OrderDto orderDto = new OrderDto();
        orderDto.setOrderId(1L);
        orderDto.setStatus("CONFIRMED");
        List<OrderItemDto> orderItems = new ArrayList<>();
        orderItems.add(new OrderItemDto(1L, "product1", 10, BigDecimal.TEN));
        orderItems.add(new OrderItemDto(1L, "product2", 20, BigDecimal.TEN));
        orderDto.setItems(orderItems);

        List<Inventory> inventoryList = new ArrayList<>();
        inventoryList.add(new Inventory(1L, "product1", 0, 0));
        inventoryList.add(new Inventory(2L, "product2", 0, 0));

        given(inventoryRepository.findByProductCodeIn(anyList())).willReturn(inventoryList);

        // Act
        inventoryOrderManageService.confirm(orderDto);

        // Assert
        verify(inventoryRepository, times(1)).findByProductCodeIn(anyList());
        verify(inventoryRepository, times(1)).saveAll(anyList());
        assertEquals("CONFIRMED", orderDto.getStatus());
    }
}
