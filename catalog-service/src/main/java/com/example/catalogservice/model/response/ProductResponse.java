/***
<p>
    Licensed under MIT License Copyright (c) 2023 Raja Kolli.
</p>
***/

package com.example.catalogservice.model.response;

public record ProductResponse(
        Long id,
        String code,
        String productName,
        String description,
        double price,
        boolean inStock) {}
