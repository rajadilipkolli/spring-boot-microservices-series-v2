/***
<p>
    Licensed under MIT License Copyright (c) 2021-2026 Raja Kolli.
</p>
***/

package com.example.orderservice.model.dtos;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;

public record OrderItemDto(
        @JsonFormat(shape = JsonFormat.Shape.STRING) Long itemId,
        String productId,
        @NotNull(message = "Quantity cannot be null")
                @Positive(message = "Quantity should be positive")
                Integer quantity,
        BigDecimal productPrice)
        implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    /**
     * Returns the unit price multiplied by quantity, rounded to two decimal places using HALF_UP.
     *
     * @throws NullPointerException if the unit price or quantity is null
     */
    public BigDecimal getPrice() {
        return this.productPrice()
                .multiply(BigDecimal.valueOf(this.quantity()))
                .setScale(2, RoundingMode.HALF_UP);
    }
}
