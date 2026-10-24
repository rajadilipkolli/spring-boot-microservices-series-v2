/***
<p>
    Licensed under MIT License Copyright (c) 2023-2026 Raja Kolli.
</p>
***/

package com.example.inventoryservice.inventory.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.example.common.dto.ProductDto;
import java.math.BigDecimal;
import java.util.Optional;
import org.instancio.junit.InstancioExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({MockitoExtension.class, InstancioExtension.class})
class ProductManageServiceImplTest {

    @Captor private ArgumentCaptor<Inventory> argumentCaptor;

    @Mock private InventoryRepository inventoryRepository;
    @Mock private InventoryJOOQRepository inventoryJOOQRepository;

    @InjectMocks private ProductManageServiceImpl productManageService;

    @Test
    void manage() {
        // Arrange
        ProductDto productDto = new ProductDto("P001", "Product 1", "Desc", BigDecimal.TEN, false);

        given(inventoryJOOQRepository.existsByProductCode(productDto.code())).willReturn(false);
        given(inventoryRepository.save(argumentCaptor.capture()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        // Act
        productManageService.manage(productDto);

        // Assert
        verify(inventoryRepository, times(1)).save(argumentCaptor.capture());
        assertThat(argumentCaptor.getValue())
                .isNotNull()
                .satisfies(
                        inventory -> {
                            assertThat(inventory.getProductCode()).isNotBlank();
                            assertThat(inventory.getId()).isNull();
                            assertThat(inventory.isDeleted()).isFalse();
                        });
    }

    @Test
    void manageWhenDeletedAndActiveShouldSoftDelete() {
        ProductDto productDto = new ProductDto("P001", "Product 1", "Desc", BigDecimal.TEN, true);
        Inventory activeInventory = new Inventory().setProductCode("P001").setAvailableQuantity(10);

        given(inventoryRepository.findByProductCode("P001"))
                .willReturn(Optional.of(activeInventory));
        given(inventoryRepository.save(argumentCaptor.capture()))
                .willAnswer(invocation -> invocation.getArgument(0));

        productManageService.manage(productDto);

        verify(inventoryRepository, times(1)).save(argumentCaptor.capture());
        assertThat(argumentCaptor.getValue().isDeleted()).isTrue();
    }

    @Test
    void manageWhenDeletedAndAlreadyDeletedShouldDoNothing() {
        ProductDto productDto = new ProductDto("P001", "Product 1", "Desc", BigDecimal.TEN, true);
        Inventory deletedInventory =
                new Inventory().setProductCode("P001").setAvailableQuantity(10).markDeleted();

        given(inventoryRepository.findByProductCode("P001"))
                .willReturn(Optional.of(deletedInventory));

        productManageService.manage(productDto);

        verify(inventoryRepository, times(0)).save(any());
    }

    @Test
    void manageWhenDeletedAndMissingShouldDoNothing() {
        ProductDto productDto = new ProductDto("P001", "Product 1", "Desc", BigDecimal.TEN, true);

        given(inventoryRepository.findByProductCode("P001")).willReturn(Optional.empty());

        productManageService.manage(productDto);

        verify(inventoryRepository, times(0)).save(any());
    }

    @Test
    void manageWhenNotDeletedAndSoftDeletedExistsShouldNotRecreate() {
        ProductDto productDto = new ProductDto("P001", "Product 1", "Desc", BigDecimal.TEN, false);

        given(inventoryJOOQRepository.existsByProductCode("P001")).willReturn(true);

        productManageService.manage(productDto);

        verify(inventoryRepository, times(0)).save(any());
    }
}
