package com.example.paymentservice.entities;

import javax.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "order_items")
public class OrderItem {

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "order_item_id_generator")
  @SequenceGenerator(
      name = "order_item_id_generator",
      sequenceName = "order_item_id_seq",
      allocationSize = 100)
  private Long id;

  @Column(nullable = false)
  private Long productId;

  @Column(nullable = false)
  private int quantity;

  @Column(columnDefinition = "NUMERIC(19,2)")
  private BigDecimal productPrice;

  @ManyToOne(fetch = FetchType.LAZY)
  private Order order;

  public OrderItem(Long id, Long productId, int quantity, BigDecimal productPrice, Order order) {
    this.id = id;
    this.productId = productId;
    this.quantity = quantity;
    this.productPrice = productPrice;
    this.order = order;
  }

  public OrderItem() {}

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getProductId() {
    return productId;
  }

  public void setProductId(Long productId) {
    this.productId = productId;
  }

  public int getQuantity() {
    return quantity;
  }

  public void setQuantity(int quantity) {
    this.quantity = quantity;
  }

  public BigDecimal getProductPrice() {
    return productPrice;
  }

  public void setProductPrice(BigDecimal productPrice) {
    this.productPrice = productPrice;
  }

  public Order getOrder() {
    return order;
  }

  public void setOrder(Order order) {
    this.order = order;
  }
}
