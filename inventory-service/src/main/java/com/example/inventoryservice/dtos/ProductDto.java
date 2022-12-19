/* Licensed under Apache-2.0 2022 */
package com.example.inventoryservice.dtos;

import lombok.Data;

@Data
public class ProductDto {

    private String code;

    private String productName;

    private String description;
}
