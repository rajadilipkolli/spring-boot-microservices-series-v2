/* Licensed under Apache-2.0 2023 */
package com.example.paymentservice.model.response;

public record CustomerResponse(
        Long id, String name, String email, String address, int amountAvailable) {}
