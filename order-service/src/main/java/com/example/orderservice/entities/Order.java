/***
<p>
    Licensed under MIT License Copyright (c) 2021-2026 Raja Kolli.
</p>
***/

package com.example.orderservice.entities;

import com.example.common.tsid.TsidFactorySupplier;
import com.example.orderservice.model.Address;
import io.hypersistence.utils.hibernate.id.Tsid;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
public class Order extends Auditable<String> implements Serializable {

    @Id
    @Tsid(TsidFactorySupplier.class)
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Enumerated(EnumType.STRING)
    private OrderStatus status = OrderStatus.NEW;

    private String source;

    @Embedded
    @AttributeOverrides(
            value = {
                @AttributeOverride(
                        name = "addressLine1",
                        column = @Column(name = "delivery_address_line1", nullable = false)),
                @AttributeOverride(
                        name = "addressLine2",
                        column = @Column(name = "delivery_address_line2")),
                @AttributeOverride(
                        name = "city",
                        column = @Column(name = "delivery_address_city", nullable = false)),
                @AttributeOverride(
                        name = "state",
                        column = @Column(name = "delivery_address_state", nullable = false)),
                @AttributeOverride(
                        name = "zipCode",
                        column = @Column(name = "delivery_address_zip_code", nullable = false)),
                @AttributeOverride(
                        name = "country",
                        column = @Column(name = "delivery_address_country", nullable = false)),
            })
    private Address deliveryAddress;

    @Version private Short version;

    @Column(name = "deleted", nullable = false)
    private boolean deleted = false;

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

    /**
     * Confirms a newly created order and clears its failure source.
     *
     * @throws IllegalStateException if the order is not in {@link OrderStatus#NEW} status
     */
    public void confirm() {
        if (this.status != OrderStatus.NEW) {
            throw new IllegalStateException("Cannot confirm order in state: " + this.status);
        }
        this.status = OrderStatus.CONFIRMED;
        this.source = null;
    }

    /**
     * Rolls back a newly created order.
     *
     * @param source the failure source (e.g. PAYMENT or INVENTORY)
     * @throws IllegalStateException if the order is not in {@link OrderStatus#NEW} status
     */
    public void rollback(String source) {
        if (this.status != OrderStatus.NEW) {
            throw new IllegalStateException("Cannot rollback order in state: " + this.status);
        }
        this.status = OrderStatus.ROLLBACK;
        this.source = source;
    }

    /**
     * Rejects a newly created order.
     *
     * @param source the failure source
     * @throws IllegalStateException if the order is not in {@link OrderStatus#NEW} status
     */
    public void reject(String source) {
        if (this.status != OrderStatus.NEW) {
            throw new IllegalStateException("Cannot reject order in state: " + this.status);
        }
        this.status = OrderStatus.REJECTED;
        this.source = source;
    }

    /**
     * Marks a newly created order as failed.
     *
     * @param source the failure source
     * @throws IllegalStateException if the order is not in {@link OrderStatus#NEW} status
     */
    public void fail(String source) {
        if (this.status != OrderStatus.NEW) {
            throw new IllegalStateException("Cannot fail order in state: " + this.status);
        }
        this.status = OrderStatus.FAILED;
        this.source = source;
    }

    /**
     * Checks if the order is in a terminal lifecycle state.
     *
     * @return true if the order is CONFIRMED, REJECTED, ROLLBACK, or FAILED
     * @throws NullPointerException if the current status is null
     */
    public boolean isTerminal() {
        return switch (this.status) {
            case CONFIRMED, REJECTED, ROLLBACK, FAILED -> true;
            default -> false;
        };
    }

    /**
     * Transitions this order to the specified target status with an optional source.
     *
     * @param targetStatus CONFIRMED, ROLLBACK, REJECTED, or FAILED
     * @param source the failure source to store, which may be null; ignored and cleared for
     *     confirmation
     * @throws IllegalArgumentException if the target status is NEW or ACCEPT
     * @throws IllegalStateException if the current status is not NEW
     * @throws NullPointerException if the target status is null
     */
    public void transitionTo(OrderStatus targetStatus, String source) {
        switch (targetStatus) {
            case CONFIRMED -> confirm();
            case ROLLBACK -> rollback(source);
            case REJECTED -> reject(source);
            case FAILED -> fail(source);
            default ->
                    throw new IllegalArgumentException(
                            "Unsupported target transition status: " + targetStatus);
        }
    }

    /**
     * Calculates the total monetary amount of all items in this order.
     *
     * @return the sum of quantity times price for each item, treating null prices as zero, or zero
     *     if there are no items
     */
    public BigDecimal getTotalAmount() {
        return items.stream().map(OrderItem::getSubTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public boolean isDeleted() {
        return deleted;
    }

    public Order setDeleted(boolean deleted) {
        this.deleted = deleted;
        return this;
    }
}
