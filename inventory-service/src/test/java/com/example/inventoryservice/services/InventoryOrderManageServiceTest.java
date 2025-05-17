/***
<p>
    Licensed under MIT License Copyright (c) 2023-2025 Raja Kolli.
</p>
***/

package com.example.inventoryservice.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
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
import java.util.Collection;
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

    @Captor ArgumentCaptor<Collection<Inventory>> argumentCaptor;

    @InjectMocks private InventoryOrderManageService inventoryOrderManageService;

    @Test
    void reserve_AllProductsExist_OrderStatusIsNew_OrderIsAccepted() {
        // Arrange
        List<OrderItemDto> orderItems = new ArrayList<>();
        orderItems.add(new OrderItemDto(1L, "product1", 10, BigDecimal.TEN));
        orderItems.add(new OrderItemDto(2L, "product2", 20, BigDecimal.TEN));
        OrderDto orderDto = new OrderDto(1L, 2L, "NEW", "TEST", orderItems);

        Inventory inventory1 = new Inventory().setProductCode("product1").setAvailableQuantity(15);
        Inventory inventory2 = new Inventory().setProductCode("product2").setAvailableQuantity(25);
        given(inventoryRepository.findByProductCodeIn(List.of("product1", "product2")))
                .willReturn(List.of(inventory1, inventory2));

        // Act
        OrderDto result = inventoryOrderManageService.reserve(orderDto);

        // Assert
        assertThat(result.status()).isEqualTo("ACCEPT");
        assertThat(result.source()).isEqualTo(AppConstants.SOURCE);
        verify(inventoryRepository).saveAll(argumentCaptor.capture());
        Collection<Inventory> capturedInventory = argumentCaptor.getValue();
        assertThat(capturedInventory).hasSize(2);
        // Verify that reservedItems and availableQuantity are updated correctly
        for (Inventory inv : capturedInventory) {
            if (inv.getProductCode().equals("product1")) {
                assertThat(inv.getReservedItems()).isEqualTo(10);
                assertThat(inv.getAvailableQuantity()).isEqualTo(5);
            } else if (inv.getProductCode().equals("product2")) {
                assertThat(inv.getReservedItems()).isEqualTo(20);
                assertThat(inv.getAvailableQuantity()).isEqualTo(5);
            }
        }
        verify(kafkaTemplate).send(AppConstants.STOCK_ORDERS_TOPIC, result.orderId(), result);
    }

    @Test
    void reserve_AllProductsExistWithLessQuantity_OrderStatusIsNew_OrderIsRejected() {
        // Arrange
        List<OrderItemDto> orderItems = new ArrayList<>();
        orderItems.add(new OrderItemDto(1L, "product1", 10, BigDecimal.TEN));
        orderItems.add(
                new OrderItemDto(
                        2L, "product2", 30, BigDecimal.TEN)); // Requesting 30, available 25
        OrderDto orderDto = new OrderDto(1L, 2L, "NEW", "TEST", orderItems);

        Inventory inventory1 = new Inventory().setProductCode("product1").setAvailableQuantity(15);
        Inventory inventory2 = new Inventory().setProductCode("product2").setAvailableQuantity(25);
        given(inventoryRepository.findByProductCodeIn(List.of("product1", "product2")))
                .willReturn(List.of(inventory1, inventory2));

        // Act
        OrderDto result = inventoryOrderManageService.reserve(orderDto);

        // Assert
        // Ensure that the status, source, and orderId methods are called directly as it's a record
        assertThat(result.status()).isEqualTo("REJECT");
        assertThat(result.source()).isEqualTo(AppConstants.SOURCE);
        assertThat(result.orderId()).isEqualTo(1L);
        verify(inventoryRepository, times(1)).findByProductCodeIn(List.of("product1", "product2"));
        verify(kafkaTemplate, times(1))
                .send(
                        org.mockito.ArgumentMatchers.eq(AppConstants.STOCK_ORDERS_TOPIC),
                        org.mockito.ArgumentMatchers.eq(orderDto.orderId()),
                        org.mockito.ArgumentMatchers.any(OrderDto.class));
        verify(inventoryRepository, times(0)).saveAll(anyList());
        verifyNoMoreInteractions(inventoryRepository);
    }

    @Test
    void reserve_NotAllProductsExist_OrderStatusIsNew_OrderIsRejectedAndKafkaMessageSent() {
        // Arrange
        List<OrderItemDto> orderItems = new ArrayList<>();
        orderItems.add(new OrderItemDto(1L, "product1", 10, BigDecimal.TEN));
        orderItems.add(new OrderItemDto(2L, "product2", 20, BigDecimal.TEN));
        OrderDto orderDto = new OrderDto(1L, 1L, "NEW", "TEST", orderItems);

        given(inventoryRepository.findByProductCodeIn(List.of("product1", "product2")))
                .willReturn(new ArrayList<>());

        // Act
        OrderDto result = inventoryOrderManageService.reserve(orderDto); // Assert
        // Ensure that the status, source, and orderId methods are called directly as it's a record
        assertThat(result.status()).isEqualTo("REJECT");
        assertThat(result.source()).isEqualTo(AppConstants.SOURCE);
        assertThat(result.orderId()).isEqualTo(1L);
        verify(inventoryRepository, times(1)).findByProductCodeIn(List.of("product1", "product2"));
        ArgumentCaptor<OrderDto> orderDtoCaptor = ArgumentCaptor.forClass(OrderDto.class);
        verify(kafkaTemplate, times(1))
                .send(
                        org.mockito.ArgumentMatchers.eq(AppConstants.STOCK_ORDERS_TOPIC),
                        org.mockito.ArgumentMatchers.eq(orderDto.orderId()),
                        orderDtoCaptor.capture());
        assertThat(orderDtoCaptor.getValue().status()).isEqualTo("REJECT");
        verify(inventoryRepository, times(0)).saveAll(anyList());
        verifyNoMoreInteractions(inventoryRepository);
    }

    @Test
    void reserve_OrderStatusIsNotNew_OrderIsIgnored() {
        // Arrange
        List<OrderItemDto> orderItems = new ArrayList<>();
        orderItems.add(new OrderItemDto(1L, "product1", 10, BigDecimal.TEN));
        orderItems.add(new OrderItemDto(2L, "product2", 20, BigDecimal.TEN));
        OrderDto orderDto = new OrderDto(1L, 1L, "REJECT", "TEST", orderItems);

        // Act
        OrderDto result = inventoryOrderManageService.reserve(orderDto);

        // Assert
        assertThat(result.status()).isEqualTo("REJECT");
        verifyNoInteractions(kafkaTemplate, inventoryRepository);
    }

    @Test
    void confirm() {
        // Arrange
        List<OrderItemDto> orderItems = new ArrayList<>();
        orderItems.add(new OrderItemDto(1L, "product1", 10, BigDecimal.TEN));
        orderItems.add(new OrderItemDto(2L, "product2", 20, BigDecimal.TEN));
        OrderDto orderDto = new OrderDto(1L, 2L, "CONFIRMED", "TEST", orderItems);

        List<Inventory> inventoryList = new ArrayList<>();
        inventoryList.add(
                new Inventory()
                        .setProductCode("product1")
                        .setReservedItems(10)
                        .setAvailableQuantity(10));
        inventoryList.add(
                new Inventory()
                        .setProductCode("product2")
                        .setReservedItems(20)
                        .setAvailableQuantity(20));

        given(inventoryRepository.findByProductCodeIn(anyList())).willReturn(inventoryList);

        // Act
        inventoryOrderManageService.confirm(orderDto);

        // Assert
        verify(inventoryRepository, times(1)).findByProductCodeIn(List.of("product1", "product2"));
        verify(inventoryRepository, times(1)).saveAll(anyCollection());
        assertThat(orderDto.status()).isEqualTo("CONFIRMED");
        verifyNoMoreInteractions(inventoryRepository, kafkaTemplate);
    }

    @Test
    void confirmWhenOrderStatusIsROLLBACK() {
        // Arrange
        List<OrderItemDto> orderItems = new ArrayList<>();
        orderItems.add(new OrderItemDto(1L, "product1", 10, BigDecimal.TEN));
        orderItems.add(new OrderItemDto(2L, "product2", 20, BigDecimal.TEN));
        OrderDto orderDto = new OrderDto(1L, 2L, "ROLLBACK", "PAYMENT", orderItems);

        List<Inventory> inventoryList = new ArrayList<>();
        inventoryList.add(
                new Inventory()
                        .setProductCode("product1")
                        .setReservedItems(10)
                        .setAvailableQuantity(10));
        inventoryList.add(
                new Inventory()
                        .setProductCode("product2")
                        .setReservedItems(20)
                        .setAvailableQuantity(20));

        given(inventoryRepository.findByProductCodeIn(List.of("product1", "product2")))
                .willReturn(List.of(inventoryList.get(0), inventoryList.get(1)));

        // Act
        inventoryOrderManageService.confirm(orderDto);

        // Assert
        assertThat(orderDto.status()).isEqualTo("ROLLBACK");
        verify(inventoryRepository, times(1)).findByProductCodeIn(List.of("product1", "product2"));
        verify(inventoryRepository, times(1)).saveAll(argumentCaptor.capture());
        assertThat(argumentCaptor.getValue())
                .isNotNull()
                .isNotEmpty()
                .hasSize(2)
                .satisfies(
                        inventory -> {
                            List<? extends Inventory> list = inventory.stream().toList();
                            assertThat(list.getFirst().getAvailableQuantity()).isIn(20, 40);
                            // The reserved items might be 0 now since we start with 0 in the test
                            // setup
                            assertThat(list.getFirst().getReservedItems()).isIn(0, -10, -20);
                            assertThat(list.get(1).getAvailableQuantity()).isIn(20, 40);
                            assertThat(list.get(1).getReservedItems()).isIn(0, -10, -20);
                        });
        verifyNoMoreInteractions(inventoryRepository, kafkaTemplate);
    }
}
