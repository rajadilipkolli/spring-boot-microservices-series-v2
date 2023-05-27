/* Licensed under Apache-2.0 2023 */
package com.example.inventoryservice.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import com.example.common.dtos.OrderDto;
import com.example.common.dtos.OrderItemDto;
import com.example.inventoryservice.entities.Inventory;
import com.example.inventoryservice.repositories.InventoryRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Disabled;
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
        orderItems.add(new OrderItemDto(1L, "product2", 20, BigDecimal.TEN));

        orderDto.setItems(orderItems);

        when(inventoryRepository.findByProductCodeIn(anyList()))
                .thenReturn(
                        new ArrayList<>(
                                Arrays.asList(
                                        new Inventory(1L, "product1", 10, 0),
                                        new Inventory(2L, "product2", 10, 0))));

        // Act
        inventoryOrderManageService.reserve(orderDto);

        // Assert
        assertEquals("ACCEPT", orderDto.getStatus());
    }

    @Test
    @Disabled
    void testReserve_NotAllProductsExist_OrderStatusIsNew_OrderIsRejected() {
        // Arrange
        OrderDto orderDto = new OrderDto();
        orderDto.setOrderId(1L);
        orderDto.setStatus("NEW");

        List<OrderItemDto> orderItems = new ArrayList<>();
        orderItems.add(new OrderItemDto(1L, "product1", 10, BigDecimal.TEN));
        orderItems.add(new OrderItemDto(1L, "product2", 20, BigDecimal.TEN));
        orderDto.setItems(orderItems);

        when(inventoryRepository.findByProductCodeIn(anyList()))
                .thenReturn(new ArrayList<>(List.of(new Inventory(1L, "product1", 10, 0))));

        // Act
        inventoryOrderManageService.reserve(orderDto);

        // Assert
        assertEquals("REJECT", orderDto.getStatus());
    }

    @Test
    @Disabled
    void testReserve_OrderStatusIsNotNew_OrderIsRejected() {
        // Arrange
        OrderDto orderDto = new OrderDto();
        orderDto.setOrderId(1L);
        orderDto.setStatus("APPROVED");

        List<OrderItemDto> orderItems = new ArrayList<>();
        orderItems.add(new OrderItemDto(1L, "product1", 10, BigDecimal.TEN));
        orderItems.add(new OrderItemDto(1L, "product2", 20, BigDecimal.TEN));

        orderDto.setItems(orderItems);

        // Act
        inventoryOrderManageService.reserve(orderDto);

        // Assert
        assertEquals("REJECT", orderDto.getStatus());
    }

    @Test
    @Disabled
    void testReserve() {
        // Arrange
        OrderDto orderDto = new OrderDto();
        orderDto.setOrderId(1L);
        orderDto.setStatus("NEW");
        List<OrderItemDto> orderItems = new ArrayList<>();
        OrderItemDto orderItem1 = new OrderItemDto();
        orderItem1.setProductId("product1");
        orderItem1.setQuantity(1);
        orderItems.add(orderItem1);
        orderDto.setItems(orderItems);

        List<Inventory> inventoryList = new ArrayList<>();
        Inventory inventory1 = new Inventory();
        inventory1.setProductCode("product1");
        inventory1.setAvailableQuantity(10);
        inventoryList.add(inventory1);

        when(inventoryRepository.findByProductCodeIn(anyList())).thenReturn(inventoryList);

        // Act
        inventoryOrderManageService.reserve(orderDto);

        // Assert
        assertEquals("ACCEPT", orderDto.getStatus());
        assertEquals(9, inventory1.getAvailableQuantity());
    }

    @Test
    void testConfirm() {
        // Arrange
        OrderDto orderDto = new OrderDto();
        orderDto.setOrderId(1L);
        orderDto.setStatus("CONFIRMED");
        List<OrderItemDto> orderItems = new ArrayList<>();
        OrderItemDto orderItem1 = new OrderItemDto();
        orderItem1.setProductId("product1");
        orderItem1.setQuantity(1);
        orderItems.add(orderItem1);
        orderDto.setItems(orderItems);

        List<Inventory> inventoryList = new ArrayList<>();
        Inventory inventory1 = new Inventory();
        inventory1.setProductCode("product1");
        inventory1.setReservedItems(1);
        inventory1.setAvailableQuantity(9);
        inventoryList.add(inventory1);

        when(inventoryRepository.findByProductCodeIn(anyList())).thenReturn(inventoryList);

        // Act
        inventoryOrderManageService.confirm(orderDto);

        // Assert
        assertEquals("CONFIRMED", orderDto.getStatus());
        assertEquals(9, inventory1.getAvailableQuantity());
    }
}
