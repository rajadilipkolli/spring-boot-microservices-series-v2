/***
<p>
    Licensed under MIT License Copyright (c) 2021-2026 Raja Kolli.
</p>
***/

package com.example.orderservice.model.dtos;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

public record OrderItemDto(
        Long itemId,
        String productId,
        @NotNull(message = "Quantity cannot be null")
                @Positive(message = "Quantity should be positive")
                Integer quantity,
        BigDecimal productPrice)
        implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    public BigDecimal getPrice() {
        return this.productPrice().multiply(BigDecimal.valueOf(this.quantity()));
    }
}
