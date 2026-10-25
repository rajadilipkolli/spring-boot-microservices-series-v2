/*** Licensed under MIT License Copyright (c) 2023-2025 Raja Kolli. ***/
package com.example.paymentservice.model.response;

import com.example.paymentservice.jooq.tables.records.CustomersRecord;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "Customer details response")
public record CustomerResponse(
        @Schema(type = "string", description = "Customer ID", example = "1")
                @JsonFormat(shape = JsonFormat.Shape.STRING)
                Long customerId,
        @Schema(description = "Customer name", example = "John Doe") String name,
        @Schema(description = "Customer email", example = "john.doe@example.com") String email,
        @Schema(description = "Customer phone number", example = "+1234567890") String phone,
        @Schema(description = "Address line 1", example = "123 Main St") String addressLine1,
        @Schema(description = "Address line 2", example = "Suite 100") String addressLine2,
        @Schema(description = "City", example = "San Francisco") String city,
        @Schema(description = "State", example = "CA") String state,
        @Schema(description = "Zip code", example = "94105") String zipCode,
        @Schema(description = "Country", example = "USA") String country,
        @Schema(description = "Available funds amount", example = "1000.00")
                @JsonFormat(shape = JsonFormat.Shape.NUMBER_FLOAT, pattern = "0.00")
                BigDecimal amountAvailable) {

    /**
     * Copies customer details and available funds from a record into an API response.
     *
     * @param customersRecord the source customer record
     * @return the customer response without reserved funds or version metadata
     */
    public static CustomerResponse toResponse(CustomersRecord customersRecord) {
        return new CustomerResponse(
                customersRecord.getId(),
                customersRecord.getName(),
                customersRecord.getEmail(),
                customersRecord.getPhone(),
                customersRecord.getAddressLine1(),
                customersRecord.getAddressLine2(),
                customersRecord.getCity(),
                customersRecord.getState(),
                customersRecord.getZipCode(),
                customersRecord.getCountry(),
                customersRecord.getAmountAvailable());
    }
}
