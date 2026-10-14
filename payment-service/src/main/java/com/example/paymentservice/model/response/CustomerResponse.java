/*** Licensed under MIT License Copyright (c) 2023-2025 Raja Kolli. ***/
package com.example.paymentservice.model.response;

import com.example.paymentservice.jooq.tables.records.CustomersRecord;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;

public record CustomerResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING) Long customerId,
        String name,
        String email,
        String phone,
        String addressLine1,
        String addressLine2,
        String city,
        String state,
        String zipCode,
        String country,
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
