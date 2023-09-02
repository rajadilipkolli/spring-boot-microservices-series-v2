/***
<p>
    Licensed under MIT License Copyright (c) 2021-2023 Raja Kolli.
</p>
***/

package com.example.orderservice.entities;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "order_items",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "UC_ORDER_ITEMS_PRODUCT_ORDER",
                        columnNames = {"product_code", "order_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderItem {

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
}
