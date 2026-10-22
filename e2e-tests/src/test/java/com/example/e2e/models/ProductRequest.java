package com.example.e2e.models;

import java.math.BigDecimal;

public record ProductRequest(
        String productCode,
        String productName,
        String description,
        String imageUrl,
        BigDecimal price) {}
