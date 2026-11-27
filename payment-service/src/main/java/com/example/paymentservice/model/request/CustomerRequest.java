/*** Licensed under MIT License Copyright (c) 2023-2025 Raja Kolli. ***/
package com.example.paymentservice.model.request;

import com.example.paymentservice.jooq.tables.records.CustomersRecord;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

@Schema(description = "Customer creation request")
public record CustomerRequest(
        @Schema(description = "Customer full name", example = "John Doe")
                @NotBlank(message = "Name cannot be Blank")
                String name,
        @Schema(description = "Customer email address", example = "john.doe@example.com")
                @NotBlank(message = "Email cannot be Blank")
                @Email(message = "supplied email is not valid")
                String email,
        @Schema(description = "Customer phone number", example = "+1234567890")
                @NotBlank(message = "Customer Phone number is required")
                String phone,
        @Schema(description = "Address Line 1", example = "123 Main St")
                @NotBlank(message = "Address Line 1 cannot be Blank")
                String addressLine1,
        @Schema(description = "Address Line 2", example = "Suite 100") String addressLine2,
        @Schema(description = "City", example = "San Francisco")
                @NotBlank(message = "City cannot be Blank")
                String city,
        @Schema(description = "State", example = "CA") @NotBlank(message = "State cannot be Blank")
                String state,
        @Schema(description = "Zip Code", example = "94105")
                @NotBlank(message = "Zip Code cannot be Blank")
                String zipCode,
        @Schema(description = "Country", example = "USA")
                @NotBlank(message = "Country cannot be Blank")
                String country,
        @Schema(description = "Initial available funds", example = "1000.00")
                @NotNull
                @Positive(message = "AmountAvailable must be greater than 0")
                @Digits(
                        integer = 17,
                        fraction = 2,
                        message = "AmountAvailable can have at most 2 decimal places")
                BigDecimal amountAvailable) {
    /**
     * Creates an unsaved customer record from this request with zero reserved funds.
     *
     * @return a record with the supplied email unchanged and no ID or version assigned
     */
    public CustomersRecord toCustomersRecord() {
        return new CustomersRecord()
                .setName(name)
                .setEmail(email)
                .setPhone(phone)
                .setAddressLine1(addressLine1)
                .setAddressLine2(addressLine2)
                .setCity(city)
                .setState(state)
                .setZipCode(zipCode)
                .setCountry(country)
                .setAmountAvailable(amountAvailable)
                .setAmountReserved(BigDecimal.ZERO);
    }
}
