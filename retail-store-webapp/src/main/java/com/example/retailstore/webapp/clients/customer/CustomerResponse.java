/*** Licensed under MIT License Copyright (c) 2023 Raja Kolli. ***/
package com.example.retailstore.webapp.clients.customer;

import com.fasterxml.jackson.annotation.JsonFormat;

public record CustomerResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING) Long customerId,
        String name,
        String email,
        String phone,
        String address,
        int amountAvailable) {}
