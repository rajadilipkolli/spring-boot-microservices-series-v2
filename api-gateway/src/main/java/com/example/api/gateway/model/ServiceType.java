/***
<p>
    Licensed under MIT License Copyright (c) 2025-2026 Raja Kolli.
</p>
***/

package com.example.api.gateway.model;

/** Service identifiers for logging and error handling */
public enum ServiceType {
    CATALOG("catalog", "Catalog"),
    INVENTORY("inventory", "Inventory"),
    ORDER("order", "Order");

    private final String id;
    private final String displayName;

    ServiceType(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }
}
