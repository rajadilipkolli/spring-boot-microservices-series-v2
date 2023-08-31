/*** Licensed under MIT License Copyright (c) 2021-2023 Raja Kolli. ***/
package com.example.common.dtos;

import java.io.Serializable;
import java.math.BigDecimal;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class OrderItemDto implements Serializable {

    private Long itemId;

    private String productId;

    private int quantity;

    private BigDecimal productPrice;

    public BigDecimal getPrice() {
        return productPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
