/* Licensed under Apache-2.0 2021-2022 */
package com.example.inventoryservice.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class InventoryDto implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "ProductCode can't be blank")
    private String productCode;

    @PositiveOrZero(message = "Quantity can't be negative")
    private Integer availableQuantity;
}
