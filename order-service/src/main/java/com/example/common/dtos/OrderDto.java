/* Licensed under Apache-2.0 2021-2022 */
package com.example.common.dtos;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderDto implements Serializable {

    private Long orderId;

    @Positive(message = "CustomerId should be positive")
    private Long customerId;

    private String status = "NEW";

    private String source;

    @NotEmpty(message = "Order without items not valid")
    private List<OrderItemDto> items = new ArrayList<>();
}
