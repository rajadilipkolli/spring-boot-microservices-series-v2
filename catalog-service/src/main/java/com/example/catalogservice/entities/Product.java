/***
<p>
    Licensed under MIT License Copyright (c) 2021-2023 Raja Kolli.
</p>
***/

package com.example.catalogservice.entities;

import java.io.Serial;
import java.io.Serializable;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table(name = "products")
public class Product implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    @Id private Long id;

    private String code;

    private String productName;

    private String description;

    private double price;

    public Product() {}

    public Product(Long id, String code, String productName, String description, double price) {
        this.id = id;
        this.code = code;
        this.productName = productName;
        this.description = description;
        this.price = price;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public Product setCode(String code) {
        this.code = code;
        return this;
    }

    public String getProductName() {
        return productName;
    }

    public Product setProductName(String productName) {
        this.productName = productName;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public Product setDescription(String description) {
        this.description = description;
        return this;
    }

    public double getPrice() {
        return price;
    }

    public Product setPrice(double price) {
        this.price = price;
        return this;
    }
}
