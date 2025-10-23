/*** Licensed under MIT License Copyright (c) 2023-2025 Raja Kolli. ***/
package com.example.paymentservice.model.response;

public record CustomerResponse(
        Long customerId,
        String name,
        String email,
        String phone,
        String address,
        double amountAvailable) {}
