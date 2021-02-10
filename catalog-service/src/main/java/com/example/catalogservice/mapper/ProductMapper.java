package com.example.catalogservice.mapper;

import com.example.catalogservice.dtos.ProductDto;
import com.example.catalogservice.entities.Product;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ProductMapper {

    @Mapping(target = "inStock", ignore = true)
    Product toEntity(ProductDto productDto);
}
