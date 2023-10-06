/***
<p>
    Licensed under MIT License Copyright (c) 2023 Raja Kolli.
</p>
***/

package com.example.orderservice.model.response;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record OrderItemResponse(
        Long itemId, String productId, int quantity, BigDecimal productPrice, BigDecimal price) {

    public OrderItemResponse(
            Long itemId,
            String productId,
            int quantity,
            BigDecimal productPrice,
            BigDecimal price) {
        this.itemId = itemId;
        this.productId = productId;
        this.quantity = quantity;
        this.productPrice = productPrice;
        this.price =
                productPrice.multiply(new BigDecimal(quantity)).setScale(2, RoundingMode.HALF_UP);
    }
}
