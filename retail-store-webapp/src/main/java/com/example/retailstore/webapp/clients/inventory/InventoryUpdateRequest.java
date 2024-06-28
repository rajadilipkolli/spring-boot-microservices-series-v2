package com.example.retailstore.webapp.clients.inventory;

public record InventoryUpdateRequest(String productCode, Integer availableQuantity) {}
