/***
<p>
    Licensed under MIT License Copyright (c) 2023-2026 Raja Kolli.
</p>
***/

package com.example.orderservice.model.request;

import com.example.orderservice.model.Address;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

@Schema(description = "Order creation request")
public record OrderRequest(
        @Schema(description = "Customer ID", example = "1")
                @Positive(message = "CustomerId should be positive")
                Long customerId,
        @Schema(description = "List of order items")
                @NotEmpty(message = "Order without items not valid")
                List<@NotNull @Valid OrderItemRequest> items,
        @Schema(description = "Delivery address") @Valid Address deliveryAddress) {}
