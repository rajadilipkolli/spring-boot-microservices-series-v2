/*** Licensed under MIT License Copyright (c) 2023 Raja Kolli. ***/
package com.example.retailstore.webapp.clients.customer;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;

public record CustomerResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING) Long customerId,
        String name,
        String email,
        String phone,
        String addressLine1,
        String addressLine2,
        String city,
        String state,
        String zipCode,
        String country,
        BigDecimal amountAvailable) {}
