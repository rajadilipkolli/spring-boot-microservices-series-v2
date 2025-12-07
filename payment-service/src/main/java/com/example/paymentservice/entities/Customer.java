/*** Licensed under MIT License Copyright (c) 2022-2025 Raja Kolli. ***/
package com.example.paymentservice.entities;

public class Customer {

    private Long id;

    private String name;

    private String email;

    private String address;

    private String phone;

    private double amountAvailable;

    private double amountReserved;

    public Customer() {}

    public Long getId() {
        return this.id;
    }

    public String getName() {
        return this.name;
    }

    public String getEmail() {
        return this.email;
    }

    public String getAddress() {
        return this.address;
    }

    public double getAmountAvailable() {
        return this.amountAvailable;
    }

    public double getAmountReserved() {
        return this.amountReserved;
    }

    public Customer setId(final Long id) {
        this.id = id;
        return this;
    }

    public Customer setName(final String name) {
        this.name = name;
        return this;
    }

    public Customer setEmail(final String email) {
        this.email = email;
        return this;
    }

    public Customer setAddress(final String address) {
        this.address = address;
        return this;
    }

    public String getPhone() {
        return phone;
    }

    public Customer setPhone(String phone) {
        this.phone = phone;
        return this;
    }

    public Customer setAmountAvailable(final double amountAvailable) {
        this.amountAvailable = amountAvailable;
        return this;
    }

    public Customer setAmountReserved(final double amountReserved) {
        this.amountReserved = amountReserved;
        return this;
    }

    public String toString() {
        return "Customer(id="
                + this.getId()
                + ", name="
                + this.getName()
                + ", email="
                + this.getEmail()
                + ", phone="
                + this.getPhone()
                + ", address="
                + this.getAddress()
                + ", amountAvailable="
                + this.getAmountAvailable()
                + ", amountReserved="
                + this.getAmountReserved()
                + ")";
    }
}
