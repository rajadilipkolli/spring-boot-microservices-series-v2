/* Licensed under Apache-2.0 2021-2022 */
package com.example.common.dtos;

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

    private long customerId;

    private String status;

    private String source;

    private List<OrderItemDto> items = new ArrayList<>();
}
