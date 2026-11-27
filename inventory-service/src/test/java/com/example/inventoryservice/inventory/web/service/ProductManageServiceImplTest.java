/***
<p>
    Licensed under MIT License Copyright (c) 2023-2026 Raja Kolli.
</p>
***/

package com.example.inventoryservice.inventory.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.example.common.dto.ProductDto;
import com.example.inventoryservice.inventory.jooq.tables.records.InventoryRecord;
import java.math.BigDecimal;
import java.util.Optional;
import org.instancio.junit.InstancioExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith({MockitoExtension.class, InstancioExtension.class})
class ProductManageServiceImplTest {

    @Captor private ArgumentCaptor<Inventory> argumentCaptor;

    @Mock private InventoryRepository inventoryRepository;
    @Mock private InventoryJOOQRepository inventoryJOOQRepository;
    @Mock private InventoryMapper inventoryMapper;

    private InventoryRetryHandler retryHandler;
    private ProductManageServiceImpl productManageService;

    @BeforeEach
    void setUp() {
        retryHandler = new InventoryRetryHandler();
        productManageService =
                new ProductManageServiceImpl(
                        inventoryRepository,
                        inventoryJOOQRepository,
                        inventoryMapper,
                        retryHandler);
    }

    @Test
    void manage() {
        // Arrange
        ProductDto productDto = new ProductDto("P001", "Product 1", "Desc", BigDecimal.TEN, false);

        given(inventoryJOOQRepository.findByProductCode(productDto.code()))
                .willReturn(Optional.empty());
        given(inventoryRepository.save(argumentCaptor.capture()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        // Act
        productManageService.manage(productDto);

        // Assert
        assertThat(argumentCaptor.getValue()).isNotNull();
        assertThat(argumentCaptor.getValue().getProductCode()).isEqualTo("P001");
        assertThat(argumentCaptor.getValue().getAvailableQuantity()).isZero();
        assertThat(argumentCaptor.getValue().getReservedItems()).isZero();
        assertThat(argumentCaptor.getValue().isDeleted()).isFalse();
    }

    @Test
    void shouldSoftDeleteActiveInventoryWhenProductDeletedEventArrives() {
        // Arrange
        ProductDto productDto = new ProductDto("P001", "Product 1", "Desc", BigDecimal.TEN, true);
        InventoryRecord activeRecord =
                new InventoryRecord()
                        .setProductCode("P001")
                        .setQuantity(10)
                        .setReservedItems(2)
                        .setDeleted(false);
        Inventory activeInventory =
                new Inventory().setProductCode("P001").setAvailableQuantity(10).setReservedItems(2);

        given(inventoryJOOQRepository.findByProductCode("P001"))
                .willReturn(Optional.of(activeRecord));
        given(inventoryMapper.toEntity(activeRecord)).willReturn(activeInventory);
        given(inventoryRepository.save(argumentCaptor.capture()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        // Act
        productManageService.manage(productDto);

        // Assert
        Inventory saved = argumentCaptor.getValue();
        assertThat(saved.isDeleted()).isTrue();
        assertThat(saved.getAvailableQuantity()).isEqualTo(10);
        assertThat(saved.getReservedItems()).isEqualTo(2);
    }

    @Test
    void shouldIgnoreDeletedEventWhenInventoryAlreadyDeleted() {
        // Arrange
        ProductDto productDto = new ProductDto("P001", "Product 1", "Desc", BigDecimal.TEN, true);
        InventoryRecord deletedRecord =
                new InventoryRecord().setProductCode("P001").setDeleted(true);

        given(inventoryJOOQRepository.findByProductCode("P001"))
                .willReturn(Optional.of(deletedRecord));

        // Act
        productManageService.manage(productDto);

        // Assert
        verify(inventoryRepository, times(0)).save(any(Inventory.class));
    }

    @Test
    void shouldIgnoreDeletedEventWhenInventoryNotFound() {
        // Arrange
        ProductDto productDto = new ProductDto("P001", "Product 1", "Desc", BigDecimal.TEN, true);

        given(inventoryJOOQRepository.findByProductCode("P001")).willReturn(Optional.empty());

        // Act
        productManageService.manage(productDto);

        // Assert
        verify(inventoryRepository, times(0)).save(any(Inventory.class));
    }

    @Test
    void shouldIgnoreExistingInventoryWhenNewProductEventArrives() {
        // Arrange
        ProductDto productDto = new ProductDto("P001", "Product 1", "Desc", BigDecimal.TEN, false);

        given(inventoryJOOQRepository.findByProductCode("P001"))
                .willReturn(Optional.of(new InventoryRecord().setProductCode("P001")));

        // Act
        productManageService.manage(productDto);

        // Assert
        verify(inventoryRepository, times(0)).save(any(Inventory.class));
    }

    @Test
    void shouldIgnoreConcurrentInsertWhenProductCodeExistsAfterConflict() {
        // Arrange
        ProductDto productDto = new ProductDto("P001", "Product 1", "Desc", BigDecimal.TEN, false);

        given(inventoryJOOQRepository.findByProductCode("P001"))
                .willReturn(Optional.empty())
                .willReturn(Optional.of(new InventoryRecord().setProductCode("P001")));
        given(inventoryRepository.save(any(Inventory.class)))
                .willThrow(new DataIntegrityViolationException("duplicate key"));

        // Act
        productManageService.manage(productDto);

        // Assert
        verify(inventoryRepository, times(1)).save(any(Inventory.class));
        verify(inventoryJOOQRepository, times(2)).findByProductCode("P001");
    }

    @Test
    void shouldRethrowIntegrityExceptionWhenProductCodeStillMissingAfterConflict() {
        // Arrange
        ProductDto productDto = new ProductDto("P001", "Product 1", "Desc", BigDecimal.TEN, false);

        given(inventoryJOOQRepository.findByProductCode("P001")).willReturn(Optional.empty());
        given(inventoryRepository.save(any(Inventory.class)))
                .willThrow(new DataIntegrityViolationException("fatal error"));

        // Act & Assert
        assertThatThrownBy(() -> productManageService.manage(productDto))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
