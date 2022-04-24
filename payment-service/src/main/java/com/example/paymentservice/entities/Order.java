package com.example.paymentservice.entities;

import javax.persistence.*;
import javax.validation.constraints.NotEmpty;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
public class Order {

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "order_id_generator")
  @SequenceGenerator(
      name = "order_id_generator",
      sequenceName = "order_id_seq",
      allocationSize = 100)
  private Long id;

  @NotEmpty(message = "Email can't be blank")
  private String customerEmail;

  private String customerAddress;

  private Long customerId;

  private String status;

  private String source;

  public Order(
      Long id,
      String customerEmail,
      String customerAddress,
      Long customerId,
      String status,
      String source,
      List<OrderItem> items) {
    this.id = id;
    this.customerEmail = customerEmail;
    this.customerAddress = customerAddress;
    this.customerId = customerId;
    this.status = status;
    this.source = source;
    this.items = items;
  }

  @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<OrderItem> items = new ArrayList<>();

  public Order() {}

  public void addOrderItem(OrderItem orderItem) {
    items.add(orderItem);
    orderItem.setOrder(this);
  }

  public void removeOrderItem(OrderItem orderItem) {
    items.remove(orderItem);
    orderItem.setOrder(null);
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getCustomerEmail() {
    return customerEmail;
  }

  public void setCustomerEmail(String customerEmail) {
    this.customerEmail = customerEmail;
  }

  public String getCustomerAddress() {
    return customerAddress;
  }

  public void setCustomerAddress(String customerAddress) {
    this.customerAddress = customerAddress;
  }

  public Long getCustomerId() {
    return customerId;
  }

  public void setCustomerId(Long customerId) {
    this.customerId = customerId;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getSource() {
    return source;
  }

  public void setSource(String source) {
    this.source = source;
  }

  public List<OrderItem> getItems() {
    return items;
  }

  public void setItems(List<OrderItem> items) {
    this.items = items;
  }
}
