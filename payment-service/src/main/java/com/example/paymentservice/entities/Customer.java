/*** Licensed under MIT License Copyright (c) 2022-2025 Raja Kolli. ***/
package com.example.paymentservice.entities;

public class Customer {

    private Long id;

    private String name;

    private String email;

    private String addressLine1;

    private String addressLine2;

    private String city;

    private String state;

    private String zipCode;

    private String country;

    private String phone;

    private double amountAvailable;

    private double amountReserved;

    private Integer version;

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

    public String getAddressLine1() {
        return this.addressLine1;
    }

    public String getAddressLine2() {
        return this.addressLine2;
    }

    public String getCity() {
        return this.city;
    }

    public String getState() {
        return this.state;
    }

    public String getZipCode() {
        return this.zipCode;
    }

    public String getCountry() {
        return this.country;
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

    public Customer setAddressLine1(final String addressLine1) {
        this.addressLine1 = addressLine1;
        return this;
    }

    public Customer setAddressLine2(final String addressLine2) {
        this.addressLine2 = addressLine2;
        return this;
    }

    public Customer setCity(final String city) {
        this.city = city;
        return this;
    }

    public Customer setState(final String state) {
        this.state = state;
        return this;
    }

    public Customer setZipCode(final String zipCode) {
        this.zipCode = zipCode;
        return this;
    }

    public Customer setCountry(final String country) {
        this.country = country;
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

    /** Returns the optimistic locking version, or null if it has not been assigned. */
    public Integer getVersion() {
        return version;
    }

    /**
     * Sets the optimistic locking version held by this instance.
     *
     * @param version the version to retain, or null to clear it
     * @return this instance
     */
    public Customer setVersion(Integer version) {
        this.version = version;
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
                + ", addressLine1="
                + this.getAddressLine1()
                + ", addressLine2="
                + this.getAddressLine2()
                + ", city="
                + this.getCity()
                + ", state="
                + this.getState()
                + ", zipCode="
                + this.getZipCode()
                + ", country="
                + this.getCountry()
                + ", amountAvailable="
                + this.getAmountAvailable()
                + ", amountReserved="
                + this.getAmountReserved()
                + ")";
    }
}
