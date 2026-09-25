package com.example.retailstore.webapp.model;

import java.io.Serializable;

public record CartItem(String productCode, String productName, double price, int quantity) implements Serializable {}
