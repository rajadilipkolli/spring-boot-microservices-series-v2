/***
<p>
    Licensed under MIT License Copyright (c) 2021-2026 Raja Kolli.
</p>
***/

package com.example.catalogservice.entities;

import java.io.Serial;
import java.io.Serializable;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.annotation.Version;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

@Table(name = "products")
public class Product implements Serializable, Persistable<Long> {

    @Serial private static final long serialVersionUID = 1L;

    @Id private Long id;

    @Version private Long version = null;

    @Transient private boolean isNew = false;

    private String productCode;

    private String productName;

    private String description;

    private double price;

    private String imageUrl;

    public Product() {}

    public Long getId() {
        return id;
    }

    public Product setId(Long id) {
        this.id = id;
        return this;
    }

    /** Returns the optimistic locking version, or null if it has not been assigned. */
    public Long getVersion() {
        return version;
    }

    /**
     * Sets the optimistic locking version held by this instance.
     *
     * @param version the version to retain, or null to clear it
     * @return this instance
     */
    public Product setVersion(Long version) {
        this.version = version;
        return this;
    }

    public String getProductCode() {
        return productCode;
    }

    public Product setProductCode(String productCode) {
        this.productCode = productCode;
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

    public String getImageUrl() {
        return imageUrl;
    }

    public Product setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
        return this;
    }

    @Override
    @Transient
    public boolean isNew() {
        return this.isNew || this.id == null;
    }

    public Product setNew(boolean isNew) {
        this.isNew = isNew;
        return this;
    }
}
