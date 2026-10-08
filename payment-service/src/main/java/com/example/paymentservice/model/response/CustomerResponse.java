/*** Licensed under MIT License Copyright (c) 2023-2025 Raja Kolli. ***/
package com.example.paymentservice.model.response;

import com.fasterxml.jackson.annotation.JsonFormat;

public record CustomerResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING) Long customerId,
        String name,
        String email,
        String phone,
        String address,
        double amountAvailable) {}
