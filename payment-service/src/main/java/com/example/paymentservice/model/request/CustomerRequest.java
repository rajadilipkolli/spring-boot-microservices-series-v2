/* Licensed under Apache-2.0 2023 */
package com.example.paymentservice.model.request;

import jakarta.validation.constraints.NotEmpty;

public record CustomerRequest(
        @NotEmpty(message = "Name cannot be empty") String name,
        @NotEmpty(message = "Email cannot be empty") String email,
        String address,
        int amountAvailable) {}
