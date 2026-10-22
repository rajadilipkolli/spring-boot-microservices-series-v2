package com.example.retailstore.webapp.model;

import java.io.Serializable;
import java.math.BigDecimal;

public record CartItem(String productCode, String productName, BigDecimal price, int quantity)
        implements Serializable {}
