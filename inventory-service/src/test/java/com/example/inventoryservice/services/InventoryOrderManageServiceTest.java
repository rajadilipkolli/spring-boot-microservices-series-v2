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

        given(inventoryRepository.findByProductCodeIn(anyList()))
                .willReturn(
                        List.of(
                                new Inventory()
                                        .setId(1L)
                                        .setProductCode("product1")
                                        .setAvailableQuantity(10)
                                        .setReservedItems(0),
                                new Inventory()
                                        .setId(2L)
                                        .setProductCode("product2")
                                        .setAvailableQuantity(30)
                                        .setReservedItems(0)));

        // Act
        OrderDto resultOrderDto = inventoryOrderManageService.reserve(orderDto);

        // Assert
        assertThat(resultOrderDto.status()).isEqualTo("ACCEPT");
        assertThat(resultOrderDto.source()).isEqualTo(AppConstants.SOURCE);
        verify(kafkaTemplate, times(1))
                .send(AppConstants.STOCK_ORDERS_TOPIC, resultOrderDto.orderId(), resultOrderDto);
        verify(inventoryRepository, times(1)).saveAll(anyList());
        verifyNoMoreInteractions(inventoryRepository, kafkaTemplate);
    }

    @Test
    void reserve_AllProductsExistWithLessQuantity_OrderStatusIsNew_OrderIsRejected() {
        // Arrange
        List<OrderItemDto> orderItems = new ArrayList<>();
        orderItems.add(new OrderItemDto(1L, "product1", 10, BigDecimal.TEN));
        orderItems.add(new OrderItemDto(2L, "product2", 20, BigDecimal.TEN));
        OrderDto orderDto = new OrderDto(1L, 1L, "NEW", "TEST", orderItems);

        given(inventoryRepository.findByProductCodeIn(anyList()))
                .willReturn(
                        List.of(
                                new Inventory()
                                        .setId(1L)
                                        .setProductCode("product1")
                                        .setAvailableQuantity(10)
                                        .setReservedItems(0),
                                new Inventory()
                                        .setId(2L)
                                        .setProductCode("product2")
                                        .setAvailableQuantity(10)
                                        .setReservedItems(0)));

        // Act
        OrderDto resultOrderDto = inventoryOrderManageService.reserve(orderDto);

        // Assert
        assertThat(resultOrderDto.status()).isEqualTo("REJECT");
        assertThat(resultOrderDto.source()).isEqualTo(AppConstants.SOURCE);
        verify(kafkaTemplate, times(1))
                .send(AppConstants.STOCK_ORDERS_TOPIC, resultOrderDto.orderId(), resultOrderDto);
        verify(inventoryRepository, times(1)).findByProductCodeIn(anyList());
        verifyNoMoreInteractions(inventoryRepository, kafkaTemplate);
    }

    @Test
    void reserve_NotAllProductsExist_OrderStatusIsNew_OrderIsNotProcessed() {
        // Arrange
        List<OrderItemDto> orderItems = new ArrayList<>();
        orderItems.add(new OrderItemDto(1L, "product1", 10, BigDecimal.TEN));
        orderItems.add(new OrderItemDto(2L, "product2", 20, BigDecimal.TEN));
        OrderDto orderDto = new OrderDto(1L, 1L, "NEW", "TEST", orderItems);

        given(inventoryRepository.findByProductCodeIn(anyList()))
                .willReturn(
                        new ArrayList<>(
                                List.of(
                                        new Inventory()
                                                .setId(1L)
                                                .setProductCode("product1")
                                                .setAvailableQuantity(0)
                                                .setReservedItems(0))));

        // Act
        OrderDto resultOrderDto = inventoryOrderManageService.reserve(orderDto);

        // Assert
        assertThat(resultOrderDto.status()).isEqualTo("NEW");
        verify(inventoryRepository, times(1)).findByProductCodeIn(anyList());
        verifyNoInteractions(kafkaTemplate);
        verifyNoMoreInteractions(inventoryRepository);
    }

    @Test
    void reserve_OrderStatusIsNotNew_OrderIsRejected() {
        // Arrange
        List<OrderItemDto> orderItems = new ArrayList<>();
        orderItems.add(new OrderItemDto(1L, "product1", 10, BigDecimal.TEN));
        orderItems.add(new OrderItemDto(2L, "product2", 20, BigDecimal.TEN));
        OrderDto orderDto = new OrderDto(1L, 1L, "REJECT", "TEST", orderItems);

        // Act
        OrderDto resultOrderDto = inventoryOrderManageService.reserve(orderDto);

        // Assert
        assertThat(resultOrderDto.status()).isEqualTo("REJECT");
        verifyNoInteractions(kafkaTemplate, inventoryRepository);
    }

    @Test
    void confirm() {
        // Arrange
        List<OrderItemDto> orderItems = new ArrayList<>();
        orderItems.add(new OrderItemDto(1L, "product1", 10, BigDecimal.TEN));
        orderItems.add(new OrderItemDto(1L, "product2", 20, BigDecimal.TEN));
        OrderDto orderDto = new OrderDto(1L, 2L, "CONFIRMED", "TEST", orderItems);

        List<Inventory> inventoryList = new ArrayList<>();
        inventoryList.add(
                new Inventory()
                        .setId(1L)
                        .setProductCode("product1")
                        .setAvailableQuantity(0)
                        .setReservedItems(0));
        inventoryList.add(
                new Inventory()
                        .setId(2L)
                        .setProductCode("product2")
                        .setAvailableQuantity(0)
                        .setReservedItems(0));

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
        orderItems.add(new OrderItemDto(1L, "product2", 20, BigDecimal.TEN));
        OrderDto orderDto = new OrderDto(1L, 2L, "ROLLBACK", "PAYMENT", orderItems);

        List<Inventory> inventoryList = new ArrayList<>();
        inventoryList.add(
                new Inventory()
                        .setId(1L)
                        .setProductCode("product1")
                        .setAvailableQuantity(10)
                        .setReservedItems(0));
        inventoryList.add(
                new Inventory()
                        .setId(2L)
                        .setProductCode("product2")
                        .setAvailableQuantity(20)
                        .setReservedItems(0));

        given(inventoryRepository.findByProductCodeIn(List.of("product1", "product2")))
                .willReturn(inventoryList);

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
