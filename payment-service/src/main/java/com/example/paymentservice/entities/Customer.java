/*** Licensed under MIT License Copyright (c) 2022-2025 Raja Kolli. ***/
package com.example.paymentservice.entities;

import java.math.BigDecimal;

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

    private BigDecimal amountAvailable = BigDecimal.ZERO;

    private BigDecimal amountReserved = BigDecimal.ZERO;

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

    /** Returns the first address line. */
    public String getAddressLine1() {
        return this.addressLine1;
    }

    /** Returns the optional second address line. */
    public String getAddressLine2() {
        return this.addressLine2;
    }

    /** Returns the city. */
    public String getCity() {
        return this.city;
    }

    /** Returns the state or province. */
    public String getState() {
        return this.state;
    }

    /** Returns the ZIP or postal code. */
    public String getZipCode() {
        return this.zipCode;
    }

    /** Returns the country. */
    public String getCountry() {
        return this.country;
    }

    /** Returns the available balance, initially zero. */
    public BigDecimal getAmountAvailable() {
        return this.amountAvailable;
    }

    /** Returns the balance reserved for orders, initially zero. */
    public BigDecimal getAmountReserved() {
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

    /**
     * Sets the first address line.
     *
     * @param addressLine1 the first address line to store
     * @return this customer for chaining
     */
    public Customer setAddressLine1(final String addressLine1) {
        this.addressLine1 = addressLine1;
        return this;
    }

    /**
     * Sets the optional second address line.
     *
     * @param addressLine2 the optional second address line to store
     * @return this customer for chaining
     */
    public Customer setAddressLine2(final String addressLine2) {
        this.addressLine2 = addressLine2;
        return this;
    }

    /**
     * Sets the city.
     *
     * @param city the city to store
     * @return this customer for chaining
     */
    public Customer setCity(final String city) {
        this.city = city;
        return this;
    }

    /**
     * Sets the state or province.
     *
     * @param state the state or province to store
     * @return this customer for chaining
     */
    public Customer setState(final String state) {
        this.state = state;
        return this;
    }

    /**
     * Sets the ZIP or postal code.
     *
     * @param zipCode the ZIP or postal code to store
     * @return this customer for chaining
     */
    public Customer setZipCode(final String zipCode) {
        this.zipCode = zipCode;
        return this;
    }

    /**
     * Sets the country.
     *
     * @param country the country to store
     * @return this customer for chaining
     */
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

    /**
     * Stores the available balance without validation or rounding.
     *
     * @param amountAvailable the balance to retain, including null
     * @return this customer for chaining
     */
    public Customer setAmountAvailable(final BigDecimal amountAvailable) {
        this.amountAvailable = amountAvailable;
        return this;
    }

    /**
     * Stores the balance reserved for orders without validation or rounding.
     *
     * @param amountReserved the balance to retain, including null
     * @return this customer for chaining
     */
    public Customer setAmountReserved(final BigDecimal amountReserved) {
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

    /** Returns customer details, including each address field and both balances. */
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
