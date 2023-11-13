/***
<p>
    Licensed under MIT License Copyright (c) 2021-2023 Raja Kolli.
</p>
***/

package com.example.catalogservice.mapper;

import com.example.catalogservice.entities.Product;
import com.example.catalogservice.model.request.ProductRequest;
import com.example.catalogservice.model.response.ProductResponse;
import com.example.common.dtos.ProductDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface ProductMapper {

    @Mapping(target = "withInStock", ignore = true)
    ProductResponse toProductResponse(Product product);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "inStock", ignore = true)
    Product toEntity(ProductRequest productRequest);

    ProductDto toProductDto(ProductRequest productRequest);

    @Mapping(target = "inStock", ignore = true)
    @Mapping(target = "id", ignore = true)
    void mapProductWithRequest(ProductRequest productRequest, @MappingTarget Product product);
}
