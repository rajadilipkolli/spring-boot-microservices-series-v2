/*** Licensed under MIT License Copyright (c) 2021-2026 Raja Kolli. ***/
package com.example.paymentservice.model.payload;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderDto(
        @JsonFormat(shape = JsonFormat.Shape.STRING) Long orderId,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
                @Positive(message = "CustomerId should be positive")
                Long customerId,
        String status,
        String source,
        @NotEmpty(message = "Order without items not valid") List<@Valid OrderItemDto> items,
        String attemptId)
        implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    /** Creates an order with a null attempt ID. */
    public OrderDto(
            Long orderId, Long customerId, String status, String source, List<OrderItemDto> items) {
        this(orderId, customerId, status, source, items, null);
    }

    /**
     * Returns this order if the source is unchanged; otherwise returns a copy with the requested
     * source, preserving the attempt ID and all other fields.
     */
    public OrderDto withSource(String source) {
        if (Objects.equals(this.source(), source)) {
            return this;
        }
        return new OrderDto(orderId(), customerId(), status(), source, items(), attemptId());
    }

    /**
     * Returns this order if the status is unchanged; otherwise returns a copy with the requested
     * status, preserving the attempt ID and all other fields.
     */
    public OrderDto withStatus(String status) {
        if (Objects.equals(this.status(), status)) {
            return this;
        }
        return new OrderDto(orderId(), customerId(), status, source(), items(), attemptId());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OrderItemDto(
            @JsonFormat(shape = JsonFormat.Shape.STRING) Long itemId,
            String productId,
            @Positive(message = "Quantity should be positive") Integer quantity,
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
