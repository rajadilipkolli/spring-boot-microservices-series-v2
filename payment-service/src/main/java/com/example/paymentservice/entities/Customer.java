/*** Licensed under MIT License Copyright (c) 2022-2023 Raja Kolli. ***/
package com.example.paymentservice.entities;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Customer {

    private Long id;

    private String name;

    private String email;

    private String address;

    private int amountAvailable;

    private int amountReserved;
}
