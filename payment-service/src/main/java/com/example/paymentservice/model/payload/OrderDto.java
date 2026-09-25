/*** Licensed under MIT License Copyright (c) 2021-2026 Raja Kolli. ***/
package com.example.paymentservice.model.payload;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderDto(
        Long orderId,
        @Positive(message = "CustomerId should be positive") Long customerId,
        String status,
        String source,
        @NotEmpty(message = "Order without items not valid") List<@Valid OrderItemDto> items)
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
}
