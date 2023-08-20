/* Licensed under Apache-2.0 2023 */
package com.example.orderservice.model.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record OrderRequest(
        @Positive(message = "CustomerId should be positive") Long customerId,
        @NotEmpty(message = "Order without items not valid") List<OrderItemRequest> items) {}
