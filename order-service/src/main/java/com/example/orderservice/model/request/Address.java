/***
<p>
    Licensed under MIT License Copyright (c) 2024 Raja Kolli.
</p>
***/

package com.example.orderservice.model.request;

import jakarta.validation.constraints.NotBlank;

public record Address(
        @NotBlank(message = "AddressLine1 is required") String addressLine1,
        String addressLine2,
        @NotBlank(message = "City is required") String city,
        @NotBlank(message = "State is required") String state,
        @NotBlank(message = "ZipCode is required") String zipCode,
        @NotBlank(message = "Country is required") String country) {}
