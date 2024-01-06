/***
<p>
    Licensed under MIT License Copyright (c) 2021-2022 Raja Kolli.
</p>
***/

package com.example.inventoryservice.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "inventory")
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;

    @Column(name = "product_code", nullable = false, unique = true, updatable = false)
    private String productCode;

    @Column(name = "quantity")
    private Integer availableQuantity = 0;

    @Column(name = "reserved_items")
    private Integer reservedItems = 0;

    public Long getId() {
        return id;
    }

    public Inventory setId(Long id) {
        this.id = id;
        return this;
    }

    public Inventory setProductCode(String productCode) {
        this.productCode = productCode;
        return this;
    }

    public String getProductCode() {
        return productCode;
    }

    public Inventory setAvailableQuantity(Integer availableQuantity) {
        this.availableQuantity = availableQuantity;
        return this;
    }

    public Integer getAvailableQuantity() {
        return availableQuantity;
    }

    public Inventory setReservedItems(Integer reservedItems) {
        this.reservedItems = reservedItems;
        return this;
    }

    public Integer getReservedItems() {
        return reservedItems;
    }
}
