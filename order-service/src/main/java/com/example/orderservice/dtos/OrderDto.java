package com.example.orderservice.dtos;

import java.util.ArrayList;
import java.util.List;
import javax.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderDto {

    private Long orderId;

    @NotEmpty(message = "Email can't be blank")
    private String customerEmail;

    private String customerAddress;

    private List<OrderItemDto> items = new ArrayList<>();
}
