package com.example.retailstore.webapp.model;

import java.io.Serializable;
import java.util.List;

public record CartState(List<CartItem> items, double totalAmount) implements Serializable {}
