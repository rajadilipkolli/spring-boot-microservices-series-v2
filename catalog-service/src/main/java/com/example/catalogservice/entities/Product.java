/*** Licensed under MIT License Copyright (c) 2021-2023 Raja Kolli. ***/

package com.example.catalogservice.entities;

import java.io.Serial;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Table;

@Table(name = "products")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Product implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    @Id private Long id;

    private String code;

    private String productName;

    private String description;

    private double price;

    @Transient private boolean inStock;
}
