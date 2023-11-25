/***
<p>
    Licensed under MIT License Copyright (c) 2023 Raja Kolli.
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
import com.example.inventoryservice.repositories.InventoryJOOQRepository;
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
    @Mock private InventoryJOOQRepository inventoryJOOQRepository;

    @Mock private KafkaTemplate<Long, OrderDto> kafkaTemplate;

    @Captor ArgumentCaptor<Collection<Inventory>> argumentCaptor;

    @InjectMocks private InventoryOrderManageService inventoryOrderManageService;

    @Test
    void reserve_AllProductsExist_OrderStatusIsNew_OrderIsAccepted() {
        // Arrange
        OrderDto orderDto = new OrderDto();
        orderDto.setOrderId(1L);
        orderDto.setStatus("NEW");

        List<OrderItemDto> orderItems = new ArrayList<>();
        orderItems.add(new OrderItemDto(1L, "product1", 10, BigDecimal.TEN));
        orderItems.add(new OrderItemDto(2L, "product2", 20, BigDecimal.TEN));

        orderDto.setItems(orderItems);

        given(inventoryJOOQRepository.findByProductCodeIn(anyList()))
                .willReturn(
                        List.of(
                                new Inventory(1L, "product1", 10, 0),
                                new Inventory(2L, "product2", 30, 0)));

        // Act
        inventoryOrderManageService.reserve(orderDto);

        // Assert
        assertThat(orderDto.getStatus()).isEqualTo("ACCEPT");
        verify(kafkaTemplate, times(1))
                .send(AppConstants.STOCK_ORDERS_TOPIC, orderDto.getOrderId(), orderDto);
        verify(inventoryRepository, times(1)).saveAll(anyList());
        verifyNoMoreInteractions(inventoryRepository, kafkaTemplate);
    }

    @Test
    void reserve_AllProductsExistWithLessQuantity_OrderStatusIsNew_OrderIsRejected() {
        // Arrange
        OrderDto orderDto = new OrderDto();
        orderDto.setOrderId(1L);
        orderDto.setStatus("NEW");

        List<OrderItemDto> orderItems = new ArrayList<>();
        orderItems.add(new OrderItemDto(1L, "product1", 10, BigDecimal.TEN));
        orderItems.add(new OrderItemDto(2L, "product2", 20, BigDecimal.TEN));

        orderDto.setItems(orderItems);

        given(inventoryJOOQRepository.findByProductCodeIn(anyList()))
                .willReturn(
                        List.of(
                                new Inventory(1L, "product1", 10, 0),
                                new Inventory(2L, "product2", 10, 0)));

        // Act
        inventoryOrderManageService.reserve(orderDto);

        // Assert
        assertThat(orderDto.getStatus()).isEqualTo("REJECT");
        verify(kafkaTemplate, times(1))
                .send(AppConstants.STOCK_ORDERS_TOPIC, orderDto.getOrderId(), orderDto);
        verify(inventoryJOOQRepository, times(1)).findByProductCodeIn(anyList());
        verifyNoMoreInteractions(inventoryRepository, kafkaTemplate);
    }

    @Test
    void reserve_NotAllProductsExist_OrderStatusIsNew_OrderIsNotProcessed() {
        // Arrange
        OrderDto orderDto = new OrderDto();
        orderDto.setOrderId(1L);
        orderDto.setStatus("NEW");

        List<OrderItemDto> orderItems = new ArrayList<>();
        orderItems.add(new OrderItemDto(1L, "product1", 10, BigDecimal.TEN));
        orderItems.add(new OrderItemDto(2L, "product2", 20, BigDecimal.TEN));
        orderDto.setItems(orderItems);

        given(inventoryJOOQRepository.findByProductCodeIn(anyList()))
                .willReturn(new ArrayList<>(List.of(new Inventory(1L, "product1", 0, 0))));

        // Act
        inventoryOrderManageService.reserve(orderDto);

        // Assert
        assertThat(orderDto.getStatus()).isEqualTo("NEW");
        verify(inventoryJOOQRepository, times(1)).findByProductCodeIn(anyList());
        verifyNoInteractions(kafkaTemplate);
        verifyNoMoreInteractions(inventoryJOOQRepository);
    }

    @Test
    void reserve_OrderStatusIsNotNew_OrderIsRejected() {
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
    void confirm() {
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

        given(inventoryJOOQRepository.findByProductCodeIn(anyList())).willReturn(inventoryList);

        // Act
        inventoryOrderManageService.confirm(orderDto);

        // Assert
        verify(inventoryJOOQRepository, times(1))
                .findByProductCodeIn(List.of("product1", "product2"));
        verify(inventoryRepository, times(1)).saveAll(anyCollection());
        assertThat(orderDto.getStatus()).isEqualTo("CONFIRMED");
        verifyNoMoreInteractions(inventoryRepository, kafkaTemplate);
    }

    @Test
    void confirmWhenOrderStatusIsROLLBACK() {
        // Arrange
        OrderDto orderDto = new OrderDto();
        orderDto.setOrderId(1L);
        orderDto.setStatus("ROLLBACK");
        List<OrderItemDto> orderItems = new ArrayList<>();
        orderItems.add(new OrderItemDto(1L, "product1", 10, BigDecimal.TEN));
        orderItems.add(new OrderItemDto(1L, "product2", 20, BigDecimal.TEN));
        orderDto.setItems(orderItems);

        List<Inventory> inventoryList = new ArrayList<>();
        inventoryList.add(new Inventory(1L, "product1", 10, 0));
        inventoryList.add(new Inventory(2L, "product2", 20, 0));

        given(inventoryJOOQRepository.findByProductCodeIn(List.of("product1", "product2")))
                .willReturn(inventoryList);

        // Act
        inventoryOrderManageService.confirm(orderDto);

        // Assert
        assertThat(orderDto.getStatus()).isEqualTo("ROLLBACK");
        verify(inventoryJOOQRepository, times(1))
                .findByProductCodeIn(List.of("product1", "product2"));
        verify(inventoryRepository, times(1)).saveAll(argumentCaptor.capture());
        assertThat(argumentCaptor.getValue())
                .isNotNull()
                .isNotEmpty()
                .hasSize(2)
                .satisfies(
                        inventory -> {
                            List<? extends Inventory> list = inventory.stream().toList();
                            assertThat(list.getFirst().getAvailableQuantity()).isIn(20, 40);
                            assertThat(list.getFirst().getReservedItems()).isIn(-10, -20);
                            assertThat(list.get(1).getAvailableQuantity()).isIn(20, 40);
                            assertThat(list.get(1).getReservedItems()).isIn(-10, -20);
                        });
        verifyNoMoreInteractions(inventoryRepository, kafkaTemplate);
    }
}
