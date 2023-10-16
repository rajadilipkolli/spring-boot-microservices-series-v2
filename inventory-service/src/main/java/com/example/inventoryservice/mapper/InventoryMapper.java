/***
<p>
    Licensed under MIT License Copyright (c) 2021-2022 Raja Kolli.
</p>
***/

package com.example.inventoryservice.mapper;

import com.example.inventoryservice.entities.Inventory;
import com.example.inventoryservice.model.request.InventoryRequest;
import org.mapstruct.InheritConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface InventoryMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "reservedItems", ignore = true)
    Inventory toEntity(InventoryRequest inventoryRequest);

    @InheritConfiguration
    void updateInventoryFromRequest(
            InventoryRequest inventoryRequest, @MappingTarget Inventory inventory);
}
