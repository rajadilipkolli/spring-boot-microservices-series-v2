package com.example.retailstore.webapp.clients.order;

import com.example.retailstore.webapp.clients.customer.CustomerResponse;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class OrderResponse {
    private Long orderId;
    private Long customerId;
    private String status;
    private String source;
    private Address deliveryAddress;
    private LocalDateTime createdDate;

    @JsonFormat(shape = JsonFormat.Shape.NUMBER_FLOAT, pattern = "0.00")
    private BigDecimal totalPrice;

    private List<OrderItemResponse> items;
    private CustomerResponse customer; // New field to store customer details

    // No-args constructor
    public OrderResponse() {}

    // Constructor with original fields
    public OrderResponse(
            Long orderId,
            Long customerId,
            String status,
            String source,
            Address deliveryAddress,
            LocalDateTime createdDate,
            BigDecimal totalPrice,
            List<OrderItemResponse> items) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.status = status;
        this.source = source;
        this.deliveryAddress = deliveryAddress;
        this.createdDate = createdDate;
        this.totalPrice = totalPrice;
        this.items = items;
    }

    // Getters
    public Long getOrderId() {
        return orderId;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public String getStatus() {
        return status;
    }

    public String getSource() {
        return source;
    }

    public Address getDeliveryAddress() {
        return deliveryAddress;
    }

    public LocalDateTime getCreatedDate() {
        return createdDate;
    }

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }

    public List<OrderItemResponse> getItems() {
        return items;
    }

    public CustomerResponse getCustomer() {
        return customer;
    }

    // Setters
    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public void setDeliveryAddress(Address deliveryAddress) {
        this.deliveryAddress = deliveryAddress;
    }

    public void setCreatedDate(LocalDateTime createdDate) {
        this.createdDate = createdDate;
    }

    public void setTotalPrice(BigDecimal totalPrice) {
        this.totalPrice = totalPrice;
    }

    public void setItems(List<OrderItemResponse> items) {
        this.items = items;
    }

    public void setCustomer(CustomerResponse customer) {
        this.customer = customer;
    }

    // Implemented method
    public void updateCustomerDetails(CustomerResponse customerResponse) {
        this.customer = customerResponse;
    }
}
