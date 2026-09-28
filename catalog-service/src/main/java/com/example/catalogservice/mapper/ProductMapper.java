/***
<p>
    Licensed under MIT License Copyright (c) 2021-2026 Raja Kolli.
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

    /** Maps product details to a response, leaving inventory availability at its default false. */
    @Mapping(target = "inStock", ignore = true)
    ProductResponse toProductResponse(Product product);

    /** Creates a product from request details, leaving its ID and version unset. */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "version", ignore = true)
    Product toEntity(ProductRequest productRequest);

    @Mapping(target = "code", source = "productCode")
    ProductDto toProductDto(Product product);

    /** Copies request details into the supplied product, preserving its ID and version. */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "version", ignore = true)
    void mapProductWithRequest(ProductRequest productRequest, @MappingTarget Product product);
}
