/***
<p>
    Licensed under MIT License Copyright (c) 2021-2024 Raja Kolli.
</p>
***/

package com.example.orderservice.entities;

import com.example.orderservice.model.Address;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
public class Order extends Auditable<String> implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;

    private Long customerId;

    @Enumerated(EnumType.STRING)
    private OrderStatus status = OrderStatus.NEW;

    private String source;

    @Embedded
    @AttributeOverrides(
            value = {
                @AttributeOverride(
                        name = "addressLine1",
                        column = @Column(name = "delivery_address_line1")),
                @AttributeOverride(
                        name = "addressLine2",
                        column = @Column(name = "delivery_address_line2")),
                @AttributeOverride(name = "city", column = @Column(name = "delivery_address_city")),
                @AttributeOverride(
                        name = "state",
                        column = @Column(name = "delivery_address_state")),
                @AttributeOverride(
                        name = "zipCode",
                        column = @Column(name = "delivery_address_zip_code")),
                @AttributeOverride(
                        name = "country",
                        column = @Column(name = "delivery_address_country")),
            })
    private Address deliveryAddress;

    @Version private Short version;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    public Order setId(Long id) {
        this.id = id;
        return this;
    }

    public Long getId() {
        return id;
    }

    public Order setCustomerId(Long customerId) {
        this.customerId = customerId;
        return this;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public Order setStatus(OrderStatus status) {
        this.status = status;
        return this;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public Order setSource(String source) {
        this.source = source;
        return this;
    }

    public String getSource() {
        return source;
    }

    public Address getDeliveryAddress() {
        return deliveryAddress;
    }

    public Order setDeliveryAddress(Address deliveryAddress) {
        this.deliveryAddress = deliveryAddress;
        return this;
    }

    public Order setVersion(Short version) {
        this.version = version;
        return this;
    }

    public Short getVersion() {
        return version;
    }

    public Order setItems(List<OrderItem> items) {
        this.items = items;
        return this;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public void addOrderItem(OrderItem orderItem) {
        items.add(orderItem);
        orderItem.setOrder(this);
    }

    public void removeOrderItem(OrderItem orderItem) {
        items.remove(orderItem);
        orderItem.setOrder(null);
    }
}
