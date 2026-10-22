package com.example.e2e.models;

import java.math.BigDecimal;

public record CustomerRequest(
        String name,
        String email,
        String phone,
        String addressLine1,
        String addressLine2,
        String city,
        String state,
        String zipCode,
        String country,
        BigDecimal amountAvailable) {}
