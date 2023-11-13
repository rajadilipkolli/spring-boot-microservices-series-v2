/***
<p>
    Licensed under MIT License Copyright (c) 2023 Raja Kolli.
</p>
***/

package com.example.orderservice.model.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;

public record OrderItemResponse(
        Long itemId,
        String productId,
        int quantity,
        @JsonFormat(shape = JsonFormat.Shape.NUMBER_FLOAT, pattern = "0.00")
                BigDecimal productPrice,
        @JsonFormat(shape = JsonFormat.Shape.NUMBER_FLOAT, pattern = "0.00") BigDecimal price) {}
