package com.example.catalogservice.dtos;

import jakarta.validation.constraints.NotBlank;

public record ProductDto(
        @NotBlank(message = "Product code can't be blank") String code,
        String productName,
        String description,
        double price) {}
