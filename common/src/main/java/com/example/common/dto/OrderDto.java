/***
 * <p>
 * Licensed under MIT License Copyright (c) 2021-2026 Raja Kolli.
 * </p>
 ***/

package com.example.common.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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

/**
 * Shared order payload transferred across order, inventory, and payment microservices.
 *
 * @param orderId unique identifier of the order
 * @param customerId identifier of the customer placing the order
 * @param status status of the order (e.g. NEW, ACCEPT, REJECT)
 * @param source service or component initiating the event or transition
 * @param items line items belonging to the order
 * @param attemptId correlation identifier for saga execution attempts
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderDto(
        @JsonFormat(shape = JsonFormat.Shape.STRING) Long orderId,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
                @Positive(message = "CustomerId should be positive")
                Long customerId,
        String status,
        String source,
        @NotEmpty(message = "Order without items not valid")
                List<@NotNull @Valid OrderItemDto> items,
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

    /**
     * Returns a new order with the supplied attempt ID and all other fields preserved.
     *
     * @param attemptId identifier used to correlate payment and inventory responses, or null
     */
    public OrderDto withAttemptId(String attemptId) {
        return new OrderDto(orderId(), customerId(), status(), source(), items(), attemptId);
    }

    /**
     * Returns this order if the status and source are unchanged; otherwise returns a copy with the
     * requested status and source, preserving the attempt ID and all other fields.
     */
    public OrderDto withStatusAndSource(String status, String source) {
        if (Objects.equals(this.status(), status) && Objects.equals(this.source(), source)) {
            return this;
        }
        return new OrderDto(orderId(), customerId(), status, source, items(), attemptId());
    }

    /**
     * Shared line item within an {@link OrderDto}.
     *
     * @param itemId unique identifier of the line item
     * @param productId product identifier
     * @param quantity quantity ordered
     * @param productPrice unit price of the product
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
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
