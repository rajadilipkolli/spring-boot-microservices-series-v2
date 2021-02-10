package com.example.catalogservice.dtos;

import javax.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProductDto {

    @NotBlank(message = "Product code can't be blank")
    private String code;

    private String name;

    private String description;

    private double price;
}
