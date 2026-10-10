/*** Licensed under MIT License Copyright (c) 2023-2025 Raja Kolli. ***/
package com.example.paymentservice.model.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record CustomerRequest(
        @NotBlank(message = "Name cannot be Blank") String name,
        @NotBlank(message = "Email cannot be Blank") @Email(message = "supplied email is not valid")
                String email,
        @NotBlank(message = "Customer Phone number is required") String phone,
        @NotBlank(message = "Address Line 1 cannot be Blank") String addressLine1,
        String addressLine2,
        @NotBlank(message = "City cannot be Blank") String city,
        @NotBlank(message = "State cannot be Blank") String state,
        @NotBlank(message = "Zip Code cannot be Blank") String zipCode,
        @NotBlank(message = "Country cannot be Blank") String country,
        @Positive(message = "AmountAvailable must be greater than 0") double amountAvailable) {}
