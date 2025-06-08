/***
<p>
    Licensed under MIT License Copyright (c) 2025 Raja Kolli.
</p>
***/

package com.example.api.gateway.model;

/** Service identifiers for logging and error handling */
public enum ServiceType {
    CATALOG("catalog"),
    INVENTORY("inventory");

    private final String id;

    ServiceType(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }
}
