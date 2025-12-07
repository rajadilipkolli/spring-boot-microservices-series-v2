/***
<p>
    Licensed under MIT License Copyright (c) 2023-2024 Raja Kolli.
</p>
***/

package com.example.inventoryservice.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.example.inventoryservice.entities.Inventory;
import com.example.inventoryservice.model.payload.ProductDto;
import com.example.inventoryservice.repositories.InventoryRepository;
import org.instancio.Instancio;
import org.instancio.junit.InstancioExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({MockitoExtension.class, InstancioExtension.class})
class ProductManageServiceTest {

    @Captor private ArgumentCaptor<Inventory> argumentCaptor;

    @Mock private InventoryRepository inventoryRepository;

    @InjectMocks private ProductManageService productManageService;

    @Test
    void manage() {
        // Arrange

        ProductDto productDto = Instancio.create(ProductDto.class);

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
                        });
    }
}
