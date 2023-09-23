/*** Licensed under MIT License Copyright (c) 2023 Raja Kolli. ***/
package com.example.paymentservice.model.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CustomerRequest(
        @NotBlank(message = "Name cannot be Blank") String name,
        @NotBlank(message = "Email cannot be Blank") @Email(message = "supplied email is not valid")
                String email,
        String address,
        int amountAvailable) {}
