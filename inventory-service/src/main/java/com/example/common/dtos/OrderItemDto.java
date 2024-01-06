/***
<p>
    Licensed under MIT License Copyright (c) 2021-2024 Raja Kolli.
</p>
***/

package com.example.common.dtos;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.StringJoiner;

public class OrderItemDto implements Serializable {

    private Long itemId;

    private String productId;

    private int quantity;

    private BigDecimal productPrice;

    public OrderItemDto() {}

    public OrderItemDto(Long itemId, String productId, int quantity, BigDecimal productPrice) {
        this.itemId = itemId;
        this.productId = productId;
        this.quantity = quantity;
        this.productPrice = productPrice;
    }

    public Long getItemId() {
        return itemId;
    }

    public void setItemId(Long itemId) {
        this.itemId = itemId;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getProductPrice() {
        return productPrice;
    }

    public void setProductPrice(BigDecimal productPrice) {
        this.productPrice = productPrice;
    }

    public BigDecimal getPrice() {
        return productPrice.multiply(new BigDecimal(quantity));
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", OrderItemDto.class.getSimpleName() + "[", "]")
                .add("itemId=" + itemId)
                .add("productId='" + productId + "'")
                .add("quantity=" + quantity)
                .add("productPrice=" + productPrice)
                .toString();
    }
}
