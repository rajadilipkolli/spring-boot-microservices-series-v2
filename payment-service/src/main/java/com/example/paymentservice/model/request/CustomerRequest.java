/*** Licensed under MIT License Copyright (c) 2023 Raja Kolli. ***/
package com.example.paymentservice.model.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record CustomerRequest(
        @NotBlank(message = "Name cannot be Blank") String name,
        @NotBlank(message = "Email cannot be Blank") @Email(message = "supplied email is not valid")
                String email,
        String address,
        @Positive(message = "AmountAvailable must be greater than 0") int amountAvailable) {}
