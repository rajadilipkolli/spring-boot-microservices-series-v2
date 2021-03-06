package com.example.orderservice.dtos;

import java.math.BigDecimal;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class OrderItemDto {

    private Long itemId;

    private Long productId;

    private int quantity;

    private BigDecimal productPrice;

    public BigDecimal getPrice() {
        return productPrice.multiply(new BigDecimal(quantity));
    }
}
