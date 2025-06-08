/***
<p>
    Licensed under MIT License Copyright (c) 2023-2024 Raja Kolli.
</p>
***/

package com.example.orderservice.model.request;

import com.example.orderservice.model.Address;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record OrderRequest(
        @Positive(message = "CustomerId should be positive") Long customerId,
        @Valid @NotEmpty(message = "Order without items not valid") List<OrderItemRequest> items,
        @Valid Address deliveryAddress) {}
