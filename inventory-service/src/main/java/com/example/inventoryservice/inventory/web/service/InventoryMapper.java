/***
<p>
    Licensed under MIT License Copyright (c) 2021-2025 Raja Kolli.
</p>
***/

package com.example.inventoryservice.inventory.web.service;

import com.example.inventoryservice.inventory.domain.models.InventoryRequest;
import com.example.inventoryservice.inventory.domain.models.InventoryResponse;
import com.example.inventoryservice.inventory.jooq.tables.records.InventoryRecord;
import org.mapstruct.InheritConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
interface InventoryMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "reservedItems", ignore = true)
    Inventory toEntity(InventoryRequest inventoryRequest);

    @InheritConfiguration
    void updateInventoryFromRequest(
            InventoryRequest inventoryRequest, @MappingTarget Inventory inventory);

    InventoryResponse toResponse(Inventory inventory);

    /** Maps stored quantity to available quantity in the response; returns null for null input. */
    @Mapping(target = "availableQuantity", source = "quantity")
    InventoryResponse toResponse(InventoryRecord inventoryRecord);
}
