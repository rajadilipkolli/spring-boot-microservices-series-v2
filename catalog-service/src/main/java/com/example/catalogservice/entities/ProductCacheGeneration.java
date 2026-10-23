/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.catalogservice.entities;

import java.io.Serial;
import java.io.Serializable;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

@Table(name = "product_cache_generation")
public class ProductCacheGeneration implements Serializable, Persistable<Long> {

    @Serial private static final long serialVersionUID = 1L;

    @Id private Long id;

    private String generation;

    @Transient private boolean isNew = false;

    public ProductCacheGeneration setId(Long id) {
        this.id = id;
        return this;
    }

    public String getGeneration() {
        return generation;
    }

    public ProductCacheGeneration setGeneration(String generation) {
        this.generation = generation;
        return this;
    }

    @Override
    public @Nullable Long getId() {
        return this.id;
    }

    @Override
    @Transient
    public boolean isNew() {
        return this.isNew || this.id == null;
    }

    public ProductCacheGeneration setNew(boolean isNew) {
        this.isNew = isNew;
        return this;
    }
}
