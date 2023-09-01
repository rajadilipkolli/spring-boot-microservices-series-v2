/***
<p>
    Licensed under MIT License Copyright (c) 2021-2022 Raja Kolli.
</p>
***/

package com.example.inventoryservice.mapper;

import com.example.inventoryservice.dtos.InventoryDto;
import com.example.inventoryservice.entities.Inventory;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface InventoryMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "reservedItems", ignore = true)
    Inventory toEntity(InventoryDto inventoryDto);
}
