/* Licensed under Apache-2.0 2023 */
package com.example.inventoryservice.services;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

@ExtendWith(MockitoExtension.class)
class InventoryOrderManageServiceTest {

    @Mock private InventoryRepository inventoryRepository;

    @Mock private KafkaTemplate<Long, OrderDto> kafkaTemplate;

    @Captor ArgumentCaptor<List<Inventory>> argumentCaptor;

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
        assertThat(orderDto.getStatus()).isEqualTo("ACCEPT");
        verify(inventoryRepository, times(1)).saveAll(anyList());
        verify(kafkaTemplate, times(1))
                .send(AppConstants.STOCK_ORDERS_TOPIC, orderDto.getOrderId(), orderDto);
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
        assertThat(orderDto.getStatus()).isEqualTo("REJECT");
        verify(inventoryRepository, times(1)).findByProductCodeInAndQuantityAvailable(anyList());
        verify(kafkaTemplate, times(1))
                .send(AppConstants.STOCK_ORDERS_TOPIC, orderDto.getOrderId(), orderDto);
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
        assertThat(orderDto.getStatus()).isEqualTo("REJECT");
        verifyNoInteractions(kafkaTemplate, inventoryRepository);
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
        assertThat(orderDto.getStatus()).isEqualTo("CONFIRMED");
        verifyNoMoreInteractions(inventoryRepository, kafkaTemplate);
    }

    @Test
    void testConfirmWhenOrderStatusIsROLLBACK() {
        // Arrange
        OrderDto orderDto = new OrderDto();
        orderDto.setOrderId(1L);
        orderDto.setStatus("ROLLBACK");
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
        assertThat(orderDto.getStatus()).isEqualTo("ROLLBACK");
        verify(inventoryRepository, times(1)).findByProductCodeIn(anyList());
        verify(inventoryRepository, times(1)).saveAll(argumentCaptor.capture());
        assertThat(argumentCaptor.getValue())
                .isNotNull()
                .isNotEmpty()
                .hasSize(2)
                .satisfies(
                        inventory -> {
                            assertThat(inventory.get(0).getAvailableQuantity()).isIn(10, 20);
                            assertThat(inventory.get(0).getReservedItems()).isIn(-10, -20);
                            assertThat(inventory.get(1).getAvailableQuantity()).isIn(10, 20);
                            assertThat(inventory.get(1).getReservedItems()).isIn(-10, -20);
                        });
        verifyNoMoreInteractions(inventoryRepository, kafkaTemplate);
    }
}
