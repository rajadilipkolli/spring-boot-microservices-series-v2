/***
<p>
    Licensed under MIT License Copyright (c) 2023 Raja Kolli.
</p>
***/

package com.example.catalogservice.model.response;

import java.io.Serializable;

public record InventoryDto(String productCode, Integer availableQuantity) implements Serializable {}
