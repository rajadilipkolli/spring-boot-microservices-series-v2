/***
<p>
    Licensed under MIT License Copyright (c) 2021-2024 Raja Kolli.
</p>
***/

package com.example.common.dtos;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

public record OrderItemDto(Long itemId, String productId, int quantity, BigDecimal productPrice)
        implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    public BigDecimal getPrice() {
        return this.productPrice().multiply(BigDecimal.valueOf(this.quantity()));
    }
}
