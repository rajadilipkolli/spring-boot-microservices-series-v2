package com.example.inventoryservice.dtos;

import java.io.Serializable;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class InventoryDto implements Serializable {

    @NotBlank(message = "ProductCode can't be blank")
    private String productCode;

    @PositiveOrZero(message = "Quantity can't be negative")
    private Integer availableQuantity;
}
