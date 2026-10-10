/*** Licensed under MIT License Copyright (c) 2023-2025 Raja Kolli. ***/
package com.example.paymentservice.model.response;

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
        @JsonFormat(shape = JsonFormat.Shape.NUMBER_FLOAT, pattern = "0.00")
                BigDecimal amountAvailable) {}
