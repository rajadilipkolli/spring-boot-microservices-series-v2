/***
<p>
    Licensed under MIT License Copyright (c) 2023-2026 Raja Kolli.
</p>
***/

package com.example.inventoryservice.inventory.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.example.common.dto.OrderDto;
import com.example.inventoryservice.inventory.events.InventoryProcessedEvent;
import com.example.inventoryservice.inventory.jooq.tables.records.InventoryRecord;
import com.example.inventoryservice.inventory.utils.AppConstants;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class InventoryOrderManageServiceImplTest {

    @Mock private InventoryRepository inventoryRepository;
    @Mock private InventoryJOOQRepository inventoryJOOQRepository;
    @Mock private InventoryMapper inventoryMapper;
    @Mock private ProcessedOrderEventRepository processedOrderEventRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private MeterRegistry meterRegistry;

    @Captor ArgumentCaptor<Collection<Inventory>> argumentCaptor;

    private InventoryRetryHandler retryHandler;
    private InventoryOrderManageServiceImpl inventoryOrderManageServiceImpl;

    @BeforeEach
    void setUp() {
        retryHandler = new InventoryRetryHandler();
        inventoryOrderManageServiceImpl =
                new InventoryOrderManageServiceImpl(
                        inventoryRepository,
                        inventoryJOOQRepository,
                        inventoryMapper,
                        retryHandler,
                        processedOrderEventRepository,
                        meterRegistry,
                        eventPublisher);
    }

    /**
     * Verifies a new order with sufficient stock is accepted and moves quantities into
     * reservations.
     */
    @Test
    void reserve_AllProductsExist_OrderStatusIsNew_OrderIsAccepted() {
        // Arrange
        List<OrderDto.OrderItemDto> orderItems = new ArrayList<>();
        orderItems.add(new OrderDto.OrderItemDto(1L, "product1", 10, BigDecimal.TEN));
        orderItems.add(new OrderDto.OrderItemDto(2L, "product2", 20, BigDecimal.TEN));
        OrderDto orderDto = new OrderDto(1L, 2L, "NEW", "TEST", orderItems);

        InventoryRecord rec1 =
                new InventoryRecord()
                        .setProductCode("product1")
                        .setQuantity(15)
                        .setReservedItems(0);
        InventoryRecord rec2 =
                new InventoryRecord()
                        .setProductCode("product2")
                        .setQuantity(25)
                        .setReservedItems(0);

        Inventory inventory1 =
                new Inventory()
                        .setProductCode("product1")
                        .setAvailableQuantity(15)
                        .setReservedItems(0);
        Inventory inventory2 =
                new Inventory()
                        .setProductCode("product2")
                        .setAvailableQuantity(25)
                        .setReservedItems(0);

        given(processedOrderEventRepository.claim(eq(1L), eq("RESERVE"), any(Instant.class)))
                .willReturn(1);
        given(
                        inventoryJOOQRepository.findByProductCodeInAndDeletedFalse(
                                List.of("product1", "product2")))
                .willReturn(List.of(rec1, rec2));
        given(inventoryMapper.toEntity(rec1)).willReturn(inventory1);
        given(inventoryMapper.toEntity(rec2)).willReturn(inventory2);

        // Act
        OrderDto result = inventoryOrderManageServiceImpl.reserve(orderDto);

        // Assert
        assertThat(result.status()).isEqualTo("ACCEPT");
        assertThat(result.source()).isEqualTo("INVENTORY");
        verify(inventoryRepository).saveAll(argumentCaptor.capture());
        Collection<Inventory> updatedInventories = argumentCaptor.getValue();
        assertThat(updatedInventories).hasSize(2);
        for (Inventory inventory : updatedInventories) {
            if ("product1".equals(inventory.getProductCode())) {
                assertThat(inventory.getReservedItems()).isEqualTo(10);
                assertThat(inventory.getAvailableQuantity()).isEqualTo(5);
            } else if ("product2".equals(inventory.getProductCode())) {
                assertThat(inventory.getReservedItems()).isEqualTo(20);
                assertThat(inventory.getAvailableQuantity()).isEqualTo(5);
            }
        }
        verify(eventPublisher, times(1)).publishEvent(any(InventoryProcessedEvent.class));
        verify(processedOrderEventRepository, times(1)).updateResultStatus(1L, "RESERVE", "ACCEPT");
    }

    /**
     * Verifies an order requesting a missing product is rejected, recorded as a failure, and leaves
     * stock untouched.
     */
    @Test
    void reserve_ProductNotFound_OrderStatusIsNew_OrderIsRejected() {
        // Arrange
        List<OrderDto.OrderItemDto> orderItems = new ArrayList<>();
        orderItems.add(new OrderDto.OrderItemDto(1L, "product1", 10, BigDecimal.TEN));
        orderItems.add(new OrderDto.OrderItemDto(2L, "product2", 20, BigDecimal.TEN));
        OrderDto orderDto = new OrderDto(1L, 2L, "NEW", "TEST", orderItems);

        given(processedOrderEventRepository.claim(eq(1L), eq("RESERVE"), any(Instant.class)))
                .willReturn(1);
        given(
                        inventoryJOOQRepository.findByProductCodeInAndDeletedFalse(
                                List.of("product1", "product2")))
                .willReturn(
                        List.of(new InventoryRecord().setProductCode("product1").setQuantity(15)));

        // Act
        OrderDto result = inventoryOrderManageServiceImpl.reserve(orderDto);

        // Assert
        assertThat(result.status()).isEqualTo("REJECT");
        assertThat(result.source()).isEqualTo("INVENTORY");
        verify(inventoryRepository, never()).saveAll(anyCollection());
        verify(eventPublisher, times(1)).publishEvent(any(InventoryProcessedEvent.class));
        verify(processedOrderEventRepository, times(1)).updateResultStatus(1L, "RESERVE", "REJECT");
        verify(inventoryJOOQRepository, times(1))
                .findByProductCodeInAndDeletedFalse(List.of("product1", "product2"));
    }

    /**
     * Verifies an order whose quantity exceeds available stock is rejected, recorded as a failure,
     * and leaves stock untouched.
     */
    @Test
    void reserve_ProductQuantityNotAvailable_OrderStatusIsNew_OrderIsRejected() {
        // Arrange
        List<OrderDto.OrderItemDto> orderItems = new ArrayList<>();
        orderItems.add(new OrderDto.OrderItemDto(1L, "product1", 10, BigDecimal.TEN));
        orderItems.add(new OrderDto.OrderItemDto(2L, "product2", 20, BigDecimal.TEN));
        OrderDto orderDto = new OrderDto(1L, 2L, "NEW", "TEST", orderItems);

        InventoryRecord rec1 =
                new InventoryRecord().setProductCode("product1").setQuantity(5).setReservedItems(0);
        InventoryRecord rec2 =
                new InventoryRecord()
                        .setProductCode("product2")
                        .setQuantity(25)
                        .setReservedItems(0);

        Inventory inventory1 =
                new Inventory()
                        .setProductCode("product1")
                        .setAvailableQuantity(5)
                        .setReservedItems(0);
        Inventory inventory2 =
                new Inventory()
                        .setProductCode("product2")
                        .setAvailableQuantity(25)
                        .setReservedItems(0);

        given(processedOrderEventRepository.claim(eq(1L), eq("RESERVE"), any(Instant.class)))
                .willReturn(1);
        given(
                        inventoryJOOQRepository.findByProductCodeInAndDeletedFalse(
                                List.of("product1", "product2")))
                .willReturn(List.of(rec1, rec2));
        given(inventoryMapper.toEntity(rec1)).willReturn(inventory1);
        given(inventoryMapper.toEntity(rec2)).willReturn(inventory2);

        // Act
        OrderDto result = inventoryOrderManageServiceImpl.reserve(orderDto);

        // Assert
        assertThat(result.status()).isEqualTo("REJECT");
        assertThat(result.source()).isEqualTo("INVENTORY");
        verify(inventoryRepository, never()).saveAll(anyCollection());
        verify(eventPublisher, times(1)).publishEvent(any(InventoryProcessedEvent.class));
        verify(processedOrderEventRepository, times(1)).updateResultStatus(1L, "RESERVE", "REJECT");
        verify(inventoryJOOQRepository, times(1))
                .findByProductCodeInAndDeletedFalse(List.of("product1", "product2"));
    }

    /** Verifies orders whose status is not {@code NEW} are ignored without any side effects. */
    @Test
    void reserve_OrderStatusIsNotNew_OrderIsIgnored() {
        // Arrange
        OrderDto orderDto = new OrderDto(1L, 2L, "REJECT", "TEST", new ArrayList<>());

        // Act
        OrderDto result = inventoryOrderManageServiceImpl.reserve(orderDto);

        // Assert
        assertThat(result).isEqualTo(orderDto);
        verifyNoInteractions(
                inventoryRepository,
                inventoryJOOQRepository,
                processedOrderEventRepository,
                eventPublisher);
    }

    /**
     * Verifies a duplicate reservation without an attempt identifier is dropped and does not
     * publish an event.
     */
    @Test
    void reserve_DuplicateOrder_WithoutAttemptId_DropsDuplicate() {
        // Arrange
        OrderDto orderDto = new OrderDto(1L, 2L, "NEW", "TEST", new ArrayList<>());
        given(processedOrderEventRepository.claim(eq(1L), eq("RESERVE"), any(Instant.class)))
                .willReturn(0);

        // Act
        OrderDto result = inventoryOrderManageServiceImpl.reserve(orderDto);

        // Assert
        assertThat(result).isEqualTo(orderDto);
        verify(processedOrderEventRepository, times(1))
                .claim(eq(1L), eq("RESERVE"), any(Instant.class));
        verifyNoMoreInteractions(processedOrderEventRepository);
        verifyNoInteractions(inventoryRepository, inventoryJOOQRepository, eventPublisher);
    }

    /**
     * Verifies confirmed orders deduct item quantities from reserved stock. Missing products are
     * skipped.
     */
    @Test
    void confirm_OrderStatusIsConfirmed_InventoryIsUpdated() {
        // Arrange
        List<OrderDto.OrderItemDto> orderItems = new ArrayList<>();
        orderItems.add(new OrderDto.OrderItemDto(1L, "product1", 10, BigDecimal.TEN));
        orderItems.add(new OrderDto.OrderItemDto(2L, "product2", 20, BigDecimal.TEN));
        OrderDto orderDto = new OrderDto(1L, 2L, "CONFIRMED", "TEST", orderItems);

        InventoryRecord rec1 =
                new InventoryRecord()
                        .setProductCode("product1")
                        .setQuantity(10)
                        .setReservedItems(10);
        InventoryRecord rec2 =
                new InventoryRecord()
                        .setProductCode("product2")
                        .setQuantity(20)
                        .setReservedItems(20);

        Inventory inventory1 =
                new Inventory()
                        .setProductCode("product1")
                        .setAvailableQuantity(10)
                        .setReservedItems(10);
        Inventory inventory2 =
                new Inventory()
                        .setProductCode("product2")
                        .setAvailableQuantity(20)
                        .setReservedItems(20);

        given(
                        processedOrderEventRepository.claim(
                                eq(1L), eq("CONFIRM_CONFIRMED"), any(Instant.class)))
                .willReturn(1);
        given(inventoryJOOQRepository.findByProductCodeIn(List.of("product1", "product2")))
                .willReturn(List.of(rec1, rec2));
        given(inventoryMapper.toEntity(rec1)).willReturn(inventory1);
        given(inventoryMapper.toEntity(rec2)).willReturn(inventory2);

        // Act
        inventoryOrderManageServiceImpl.confirm(orderDto);

        // Assert
        verify(inventoryRepository).saveAll(argumentCaptor.capture());
        Collection<Inventory> updatedInventories = argumentCaptor.getValue();
        assertThat(updatedInventories).hasSize(2);
        for (Inventory inventory : updatedInventories) {
            assertThat(inventory.getReservedItems()).isZero();
        }
        verify(inventoryJOOQRepository, times(1))
                .findByProductCodeIn(List.of("product1", "product2"));
    }

    /**
     * Verifies duplicate confirmation events are skipped when the order ID and status claim already
     * exists.
     */
    @Test
    void confirm_DuplicateEvent_Skipped() {
        // Arrange
        OrderDto orderDto = new OrderDto(1L, 2L, "CONFIRMED", "TEST", List.of());
        given(
                        processedOrderEventRepository.claim(
                                eq(1L), eq("CONFIRM_CONFIRMED"), any(Instant.class)))
                .willReturn(0);

        // Act
        inventoryOrderManageServiceImpl.confirm(orderDto);

        // Assert
        verify(processedOrderEventRepository, times(1))
                .claim(eq(1L), eq("CONFIRM_CONFIRMED"), any(Instant.class));
        verifyNoMoreInteractions(processedOrderEventRepository);
        verifyNoInteractions(inventoryRepository, inventoryJOOQRepository, eventPublisher);
    }

    /** Verifies rollback orders restore item quantities from reserved back to available stock. */
    @Test
    void confirm_OrderStatusIsRollback_InventoryIsRestored() {
        // Arrange
        List<OrderDto.OrderItemDto> orderItems = new ArrayList<>();
        orderItems.add(new OrderDto.OrderItemDto(1L, "product1", 10, BigDecimal.TEN));
        orderItems.add(new OrderDto.OrderItemDto(2L, "product2", 20, BigDecimal.TEN));
        OrderDto orderDto = new OrderDto(1L, 2L, "ROLLBACK", "TEST", orderItems);

        InventoryRecord rec1 =
                new InventoryRecord()
                        .setProductCode("product1")
                        .setQuantity(0)
                        .setReservedItems(10);
        InventoryRecord rec2 =
                new InventoryRecord()
                        .setProductCode("product2")
                        .setQuantity(0)
                        .setReservedItems(20);

        Inventory inventory1 =
                new Inventory()
                        .setProductCode("product1")
                        .setAvailableQuantity(0)
                        .setReservedItems(10);
        Inventory inventory2 =
                new Inventory()
                        .setProductCode("product2")
                        .setAvailableQuantity(0)
                        .setReservedItems(20);

        given(
                        processedOrderEventRepository.claim(
                                eq(1L), eq("CONFIRM_ROLLBACK"), any(Instant.class)))
                .willReturn(1);
        given(inventoryJOOQRepository.findByProductCodeIn(List.of("product1", "product2")))
                .willReturn(List.of(rec1, rec2));
        given(inventoryMapper.toEntity(rec1)).willReturn(inventory1);
        given(inventoryMapper.toEntity(rec2)).willReturn(inventory2);

        // Act
        inventoryOrderManageServiceImpl.confirm(orderDto);

        // Assert
        verify(inventoryRepository).saveAll(argumentCaptor.capture());
        Collection<Inventory> updatedInventories = argumentCaptor.getValue();
        assertThat(updatedInventories).hasSize(2);
        for (Inventory inventory : updatedInventories) {
            assertThat(inventory.getReservedItems()).isZero();
            if ("product1".equals(inventory.getProductCode())) {
                assertThat(inventory.getAvailableQuantity()).isEqualTo(10);
            } else if ("product2".equals(inventory.getProductCode())) {
                assertThat(inventory.getAvailableQuantity()).isEqualTo(20);
            }
        }
        verify(inventoryJOOQRepository, times(1))
                .findByProductCodeIn(List.of("product1", "product2"));
    }

    /** Verifies rollback orders originating from INVENTORY do not restore stock. */
    @Test
    void confirm_OrderStatusIsRollbackFromInventorySource_InventoryIsNotRestored() {
        // Arrange
        List<OrderDto.OrderItemDto> orderItems = new ArrayList<>();
        orderItems.add(new OrderDto.OrderItemDto(1L, "product1", 10, BigDecimal.TEN));
        OrderDto orderDto = new OrderDto(1L, 2L, "ROLLBACK", "INVENTORY", orderItems);

        InventoryRecord rec1 =
                new InventoryRecord()
                        .setProductCode("product1")
                        .setQuantity(0)
                        .setReservedItems(10);
        Inventory inventory1 =
                new Inventory()
                        .setProductCode("product1")
                        .setAvailableQuantity(0)
                        .setReservedItems(10);

        given(
                        processedOrderEventRepository.claim(
                                eq(1L), eq("CONFIRM_ROLLBACK"), any(Instant.class)))
                .willReturn(1);
        given(inventoryJOOQRepository.findByProductCodeIn(List.of("product1")))
                .willReturn(List.of(rec1));
        given(inventoryMapper.toEntity(rec1)).willReturn(inventory1);

        // Act
        inventoryOrderManageServiceImpl.confirm(orderDto);

        // Assert
        verify(inventoryRepository).saveAll(argumentCaptor.capture());
        Collection<Inventory> updatedInventories = argumentCaptor.getValue();
        assertThat(updatedInventories).hasSize(1);
        Inventory updated = updatedInventories.iterator().next();
        assertThat(updated.getReservedItems()).isEqualTo(10);
        assertThat(updated.getAvailableQuantity()).isZero();
    }

    /**
     * Verifies replaying duplicate reservation emits the stored outcome and claims the reply
     * attempt.
     */
    @Test
    void reserve_DuplicateOrder_WithAttemptId_ReplaysStoredOutcome() {
        // Arrange
        OrderDto orderDto = new OrderDto(100L, 1L, "NEW", "ORDER", List.of(), "attempt-2");
        given(processedOrderEventRepository.claim(eq(100L), eq("RESERVE"), any(Instant.class)))
                .willReturn(0);
        given(
                        processedOrderEventRepository.claim(
                                eq(100L), eq("RESERVE_REPLY:attempt-2"), any(Instant.class)))
                .willReturn(1);
        given(processedOrderEventRepository.findResultStatusByOrderIdAndReserveEventType(100L))
                .willReturn(Optional.of("ACCEPT"));

        // Act
        OrderDto result = inventoryOrderManageServiceImpl.reserve(orderDto);

        // Assert
        assertThat(result.status()).isEqualTo("ACCEPT");
        assertThat(result.source()).isEqualTo(AppConstants.SOURCE);
        verify(eventPublisher, times(1)).publishEvent(any(InventoryProcessedEvent.class));
        verifyNoInteractions(inventoryRepository, inventoryJOOQRepository);
    }

    /** Verifies duplicate replay is skipped if stored outcome is missing. */
    @Test
    void reserve_DuplicateOrder_WithAttemptId_MissingStoredOutcome_SkipsReplay() {
        // Arrange
        OrderDto orderDto = new OrderDto(100L, 1L, "NEW", "ORDER", List.of(), "attempt-3");
        given(processedOrderEventRepository.claim(eq(100L), eq("RESERVE"), any(Instant.class)))
                .willReturn(0);
        given(
                        processedOrderEventRepository.claim(
                                eq(100L), eq("RESERVE_REPLY:attempt-3"), any(Instant.class)))
                .willReturn(1);
        given(processedOrderEventRepository.findResultStatusByOrderIdAndReserveEventType(100L))
                .willReturn(Optional.empty());

        // Act
        OrderDto result = inventoryOrderManageServiceImpl.reserve(orderDto);

        // Assert
        assertThat(result).isEqualTo(orderDto);
        verify(eventPublisher, never()).publishEvent(any(InventoryProcessedEvent.class));
        verifyNoInteractions(inventoryRepository, inventoryJOOQRepository);
    }
}
