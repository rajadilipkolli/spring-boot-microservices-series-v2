/***
<p>
    Licensed under MIT License Copyright (c) 2021-2024 Raja Kolli.
</p>
***/

package com.example.orderservice.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;
import org.hibernate.proxy.HibernateProxy;

@Entity
@Table(
        name = "order_items",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "UC_ORDER_ITEMS_PRODUCT_ORDER",
                        columnNames = {"product_code", "order_id"}))
public class OrderItem implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;

    @Column(nullable = false, name = "product_code")
    private String productCode;

    @Column(nullable = false)
    private int quantity;

    @Column(columnDefinition = "NUMERIC(19,2)")
    private BigDecimal productPrice;

    @ManyToOne(fetch = FetchType.LAZY)
    private Order order;

    public OrderItem setId(Long id) {
        this.id = id;
        return this;
    }

    public Long getId() {
        return id;
    }

    public OrderItem setProductCode(String productCode) {
        this.productCode = productCode;
        return this;
    }

    public String getProductCode() {
        return productCode;
    }

    public OrderItem setQuantity(int quantity) {
        this.quantity = quantity;
        return this;
    }

    public int getQuantity() {
        return quantity;
    }

    public OrderItem setProductPrice(BigDecimal productPrice) {
        this.productPrice = productPrice;
        return this;
    }

    public BigDecimal getProductPrice() {
        return productPrice;
    }

    public OrderItem setOrder(Order order) {
        this.order = order;
        return this;
    }

    public Order getOrder() {
        return order;
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null) {
            return false;
        }
        Class<?> objectEffectiveClass =
                o instanceof HibernateProxy hp
                        ? hp.getHibernateLazyInitializer().getPersistentClass()
                        : o.getClass();
        Class<?> thisEffectiveClass =
                this instanceof HibernateProxy hp
                        ? hp.getHibernateLazyInitializer().getPersistentClass()
                        : this.getClass();
        if (thisEffectiveClass != objectEffectiveClass) {
            return false;
        }
        OrderItem orderItem = (OrderItem) o;
        return Objects.equals(getProductCode(), orderItem.getProductCode());
    }

    @Override
    public final int hashCode() {
        return this instanceof HibernateProxy hp
                ? hp.getHibernateLazyInitializer().getPersistentClass().hashCode()
                : getClass().hashCode();
    }
}
