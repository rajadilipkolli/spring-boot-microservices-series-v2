/***
<p>
    Licensed under MIT License Copyright (c) 2021-2026 Raja Kolli.
</p>
***/

package com.example.inventoryservice.inventory.web.service;

import com.example.common.tsid.TsidFactorySupplier;
import io.hypersistence.utils.hibernate.id.Tsid;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "inventory")
class Inventory {

    @Id
    @Tsid(TsidFactorySupplier.class)
    private Long id;

    @Column(name = "product_code", nullable = false, unique = true, updatable = false)
    private String productCode;

    @Column(name = "quantity")
    private Integer availableQuantity = 0;

    @Column(name = "reserved_items")
    private Integer reservedItems = 0;

    @Column(name = "deleted", nullable = false)
    private boolean deleted = false;

    @Version private Short version = 0;

    public Inventory() {}

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

    /** Returns whether this instance is marked as soft deleted. */
    public boolean isDeleted() {
        return deleted;
    }

    /**
     * Changes the in-memory deletion flag without persisting it.
     *
     * @return this instance
     */
    public Inventory setDeleted(boolean deleted) {
        this.deleted = deleted;
        return this;
    }

    /**
     * Marks this instance as deleted without persisting it or changing stock quantities.
     *
     * @return this instance
     */
    public Inventory markDeleted() {
        this.deleted = true;
        return this;
    }

    public Short getVersion() {
        return version;
    }

    public Inventory setVersion(Short version) {
        this.version = version;
        return this;
    }
}
