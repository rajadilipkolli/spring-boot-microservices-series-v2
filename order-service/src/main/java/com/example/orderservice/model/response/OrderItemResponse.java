/***
<p>
    Licensed under MIT License Copyright (c) 2023 Raja Kolli.
</p>
***/

package com.example.orderservice.model.response;

import java.math.BigDecimal;

public record OrderItemResponse(
        Long itemId, String productId, int quantity, BigDecimal productPrice, BigDecimal price) {}
