/* Licensed under Apache-2.0 2023 */
package com.example.inventoryservice.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.example.common.dtos.ProductDto;
import com.example.inventoryservice.entities.Inventory;
import com.example.inventoryservice.repositories.InventoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductManageServiceTest {

    @Captor private ArgumentCaptor<Inventory> argumentCaptor;

    @Mock private InventoryRepository inventoryRepository;

    @InjectMocks private ProductManageService productManageService;

    @Test
    void testManage() {
        // Arrange
        ProductDto productDto = new ProductDto("ABC123", null, null, 10d);

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
                            assertThat(inventory.getProductCode()).isEqualTo("ABC123");
                            assertThat(inventory.getAvailableQuantity()).isZero();
                        });
    }
}
