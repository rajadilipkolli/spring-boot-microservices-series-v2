/***
<p>
    Licensed under MIT License Copyright (c) 2021-2026 Raja Kolli.
</p>
***/

package com.example.inventoryservice.model.payload;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

public record OrderDto(
        @JsonFormat(shape = JsonFormat.Shape.STRING) Long orderId,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
                @Positive(message = "CustomerId should be positive")
                Long customerId,
        String status,
        String source,
        @NotEmpty(message = "Order without items not valid")
                List<@NotNull @Valid OrderItemDto> items)
        implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    public OrderDto withSource(String source) {
        if (Objects.equals(this.source(), source)) {
            return this;
        }
        return new OrderDto(orderId(), customerId(), status(), source, items());
    }

    public OrderDto withStatus(String status) {
        if (Objects.equals(this.status(), status)) {
            return this;
        }
        return new OrderDto(orderId(), customerId(), status, source(), items());
    }

    public OrderDto withStatusAndSource(String status, String source) {
        if (Objects.equals(this.status(), status) && Objects.equals(this.source(), source)) {
            return this;
        }
        return new OrderDto(orderId(), customerId(), status, source, items());
    }

    public static record OrderItemDto(
            @JsonFormat(shape = JsonFormat.Shape.STRING) Long itemId,
            String productId,
            @NotNull(message = "Quantity cannot be null")
                    @Positive(message = "Quantity should be positive")
                    Integer quantity,
            BigDecimal productPrice)
            implements Serializable {

        @Serial private static final long serialVersionUID = 1L;

        /**
         * Returns the unit price multiplied by quantity, rounded to two decimal places using
         * HALF_UP.
         *
         * @throws NullPointerException if the unit price or quantity is null
         */
        public BigDecimal getPrice() {
            return this.productPrice()
                    .multiply(BigDecimal.valueOf(this.quantity()))
                    .setScale(2, RoundingMode.HALF_UP);
        }
    }
}
