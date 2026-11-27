/***
<p>
    Licensed under MIT License Copyright (c) 2024 Raja Kolli.
</p>
***/

package com.example.orderservice.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Mailing and delivery address")
public record Address(
        @Schema(description = "Address line 1", example = "123 Main St")
                @NotBlank(message = "AddressLine1 is required")
                String addressLine1,
        @Schema(description = "Address line 2", example = "Apt 4B") String addressLine2,
        @Schema(description = "City", example = "San Francisco")
                @NotBlank(message = "City is required")
                String city,
        @Schema(description = "State or province", example = "CA")
                @NotBlank(message = "State is required")
                String state,
        @Schema(description = "Zip or postal code", example = "94105")
                @NotBlank(message = "ZipCode is required")
                String zipCode,
        @Schema(description = "Country", example = "USA") @NotBlank(message = "Country is required")
                String country) {}
