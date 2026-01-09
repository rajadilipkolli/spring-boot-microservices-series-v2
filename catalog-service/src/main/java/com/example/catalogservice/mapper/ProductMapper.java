/***
<p>
    Licensed under MIT License Copyright (c) 2021-2024 Raja Kolli.
</p>
***/

package com.example.catalogservice.mapper;

import com.example.catalogservice.entities.Product;
import com.example.catalogservice.model.payload.ProductDto;
import com.example.catalogservice.model.request.ProductRequest;
import com.example.catalogservice.model.response.ProductResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface ProductMapper {

    @Mapping(target = "inStock", ignore = true)
    @Mapping(target = "withInStock", ignore = true)
    ProductResponse toProductResponse(Product product);

    @Mapping(target = "id", ignore = true)
    Product toEntity(ProductRequest productRequest);

    @Mapping(target = "code", source = "productCode")
    ProductDto toProductDto(ProductRequest productRequest);

    @Mapping(target = "id", ignore = true)
    void mapProductWithRequest(ProductRequest productRequest, @MappingTarget Product product);
}
