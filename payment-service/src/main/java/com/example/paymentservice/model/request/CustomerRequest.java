/*** Licensed under MIT License Copyright (c) 2023-2025 Raja Kolli. ***/
package com.example.paymentservice.model.request;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

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
        @NotNull
                @Positive(message = "AmountAvailable must be greater than 0")
                @Digits(
                        integer = 17,
                        fraction = 2,
                        message = "AmountAvailable can have at most 2 decimal places")
                BigDecimal amountAvailable) {}
