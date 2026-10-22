package com.example.retailstore.webapp.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

public record CartState(List<CartItem> items, BigDecimal totalAmount, String revision) implements Serializable {}
