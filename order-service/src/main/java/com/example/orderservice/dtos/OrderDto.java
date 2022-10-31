/* Licensed under Apache-2.0 2021-2022 */
package com.example.orderservice.dtos;

import jakarta.validation.constraints.NotEmpty;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderDto implements Serializable {

    private Long orderId;

    @NotEmpty(message = "Email can't be blank")
    private String customerEmail;

    private String customerAddress;

    private long customerId;

    private String status = "NEW";

    private String source;

    private List<OrderItemDto> items = new ArrayList<>();
}
