/*** Licensed under MIT License Copyright (c) 2021-2024 Raja Kolli. ***/
package com.example.common.dtos;

import java.io.Serializable;
import java.math.BigDecimal;

public class OrderItemDto implements Serializable {
    private Long itemId;
    private String productId;
    private int quantity;
    private BigDecimal productPrice;

    public BigDecimal getPrice() {
        return this.productPrice.multiply(BigDecimal.valueOf(this.quantity));
    }

    public Long getItemId() {
        return this.itemId;
    }

    public String getProductId() {
        return this.productId;
    }

    public int getQuantity() {
        return this.quantity;
    }

    public BigDecimal getProductPrice() {
        return this.productPrice;
    }

    public void setItemId(final Long itemId) {
        this.itemId = itemId;
    }

    public void setProductId(final String productId) {
        this.productId = productId;
    }

    public void setQuantity(final int quantity) {
        this.quantity = quantity;
    }

    public void setProductPrice(final BigDecimal productPrice) {
        this.productPrice = productPrice;
    }

    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof OrderItemDto other)) {
            return false;
        } else {
            if (!other.canEqual(this)) {
                return false;
            } else if (this.getQuantity() != other.getQuantity()) {
                return false;
            } else {
                label49:
                {
                    Object this$itemId = this.getItemId();
                    Object other$itemId = other.getItemId();
                    if (this$itemId == null) {
                        if (other$itemId == null) {
                            break label49;
                        }
                    } else if (this$itemId.equals(other$itemId)) {
                        break label49;
                    }

                    return false;
                }

                Object this$productId = this.getProductId();
                Object other$productId = other.getProductId();
                if (this$productId == null) {
                    if (other$productId != null) {
                        return false;
                    }
                } else if (!this$productId.equals(other$productId)) {
                    return false;
                }

                Object this$productPrice = this.getProductPrice();
                Object other$productPrice = other.getProductPrice();
                if (this$productPrice == null) {
                    return other$productPrice == null;
                } else return this$productPrice.equals(other$productPrice);
            }
        }
    }

    protected boolean canEqual(final Object other) {
        return other instanceof OrderItemDto;
    }

    public int hashCode() {
        int result = 1;
        result = result * 59 + this.getQuantity();
        Object $itemId = this.getItemId();
        result = result * 59 + ($itemId == null ? 43 : $itemId.hashCode());
        Object $productId = this.getProductId();
        result = result * 59 + ($productId == null ? 43 : $productId.hashCode());
        Object $productPrice = this.getProductPrice();
        result = result * 59 + ($productPrice == null ? 43 : $productPrice.hashCode());
        return result;
    }

    public String toString() {
        Long var10000 = this.getItemId();
        return "OrderItemDto(itemId="
                + var10000
                + ", productId="
                + this.getProductId()
                + ", quantity="
                + this.getQuantity()
                + ", productPrice="
                + this.getProductPrice()
                + ")";
    }

    public OrderItemDto() {}
}
