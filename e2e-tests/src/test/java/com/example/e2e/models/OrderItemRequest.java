package com.example.e2e.models;

import java.math.BigDecimal;

public record OrderItemRequest(String productCode, Integer quantity, BigDecimal productPrice) {}
