/*** Licensed under Apache-2.0 2023 ***/
package com.example.catalogservice.model.response;

import java.io.Serializable;

public record InventoryDto(String productCode, Integer availableQuantity) implements Serializable {}
