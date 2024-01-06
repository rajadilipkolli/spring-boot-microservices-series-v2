/*** Licensed under MIT License Copyright (c) 2022-2024 Raja Kolli. ***/
package com.example.paymentservice.entities;

public class Customer {

    private Long id;

    private String name;

    private String email;

    private String address;

    private int amountAvailable;

    private int amountReserved;

    public Customer() {}

    public Customer(
            final Long id,
            final String name,
            final String email,
            final String address,
            final int amountAvailable,
            final int amountReserved) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.address = address;
        this.amountAvailable = amountAvailable;
        this.amountReserved = amountReserved;
    }

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

    public int getAmountAvailable() {
        return this.amountAvailable;
    }

    public int getAmountReserved() {
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

    public Customer setAmountAvailable(final int amountAvailable) {
        this.amountAvailable = amountAvailable;
        return this;
    }

    public Customer setAmountReserved(final int amountReserved) {
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
                + ", address="
                + this.getAddress()
                + ", amountAvailable="
                + this.getAmountAvailable()
                + ", amountReserved="
                + this.getAmountReserved()
                + ")";
    }
}
