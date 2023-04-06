/* Licensed under Apache-2.0 2021-2022 */
package com.example.common.dtos;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
public class OrderItemDto implements Serializable {

    private Long itemId;

    private String productId;

    private int quantity;

    private BigDecimal productPrice;

    public BigDecimal getPrice() {
        return productPrice.multiply(new BigDecimal(quantity));
    }
}
