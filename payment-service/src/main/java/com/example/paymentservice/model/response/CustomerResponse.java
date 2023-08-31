/*** Licensed under MIT License Copyright (c) 2023 Raja Kolli. ***/
package com.example.paymentservice.model.response;

public record CustomerResponse(
        Long id, String name, String email, String address, int amountAvailable) {}
