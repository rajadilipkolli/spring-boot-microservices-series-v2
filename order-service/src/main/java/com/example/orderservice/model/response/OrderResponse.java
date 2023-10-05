/***
<p>
    Licensed under MIT License Copyright (c) 2023 Raja Kolli.
</p>
***/

package com.example.orderservice.model.response;

import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(
        Long orderId,
        Long customerId,
        String status,
        String source,
        LocalDateTime createdDate,
        List<OrderItemResponse> items) {}
