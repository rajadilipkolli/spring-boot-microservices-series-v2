package com.example.retailstore.webapp.clients.order;

import java.math.BigDecimal;

public record OrderItemRequest(String productCode, int quantity, BigDecimal productPrice) {}
