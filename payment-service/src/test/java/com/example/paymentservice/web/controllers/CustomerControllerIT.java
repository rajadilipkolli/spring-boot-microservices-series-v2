/*** Licensed under MIT License Copyright (c) 2021-2023 Raja Kolli. ***/
package com.example.paymentservice.web.controllers;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.paymentservice.common.AbstractIntegrationTest;
import com.example.paymentservice.entities.Customer;
import com.example.paymentservice.model.request.CustomerRequest;
import com.example.paymentservice.repositories.CustomerRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class CustomerControllerIT extends AbstractIntegrationTest {

    @Autowired private CustomerRepository customerRepository;

    private List<Customer> customerList = null;

    @BeforeEach
    void setUp() {
        customerRepository.deleteAll();

        customerList =
                List.of(
                        new Customer(
                                null,
                                "First Customer",
                                "first@customer.email",
                                "First Address",
                                100,
                                0),
                        new Customer(
                                null,
                                "Second Customer",
                                "second@customer.email",
                                "Second Address",
                                100,
                                0),
                        new Customer(
                                null,
                                "Third Customer",
                                "third@customer.email",
                                "Third Address",
                                100,
                                0));
        customerList = customerRepository.saveAll(customerList);
    }

    @Test
    void shouldFetchAllCustomers() throws Exception {
        this.mockMvc
                .perform(get("/api/customers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()", is(8)))
                .andExpect(jsonPath("$.data.size()", is(customerList.size())))
                .andExpect(jsonPath("$.totalElements", is(3)))
                .andExpect(jsonPath("$.pageNumber", is(1)))
                .andExpect(jsonPath("$.totalPages", is(1)))
                .andExpect(jsonPath("$.isFirst", is(true)))
                .andExpect(jsonPath("$.isLast", is(true)))
                .andExpect(jsonPath("$.hasNext", is(false)))
                .andExpect(jsonPath("$.hasPrevious", is(false)));
    }

    @Test
    void shouldFindCustomerById() throws Exception {
        Customer customer = customerList.get(0);
        Long customerId = customer.getId();

        this.mockMvc
                .perform(get("/api/customers/{id}", customerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(customer.getId()), Long.class))
                .andExpect(jsonPath("$.name", is(customer.getName())))
                .andExpect(jsonPath("$.email", is(customer.getEmail())))
                .andExpect(jsonPath("$.address", is(customer.getAddress())))
                .andExpect(jsonPath("$.amountAvailable", is(customer.getAmountAvailable())));
    }

    @Test
    void shouldReturn404WhenFetchingNonExistingCustomer() throws Exception {
        long customerId = customerList.get(0).getId() + 99_999;
        this.mockMvc
                .perform(get("/api/customers/{id}", customerId))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Content-Type", is("application/problem+json")))
                .andExpect(jsonPath("$.type", is("https://api.customers.com/errors/not-found")))
                .andExpect(jsonPath("$.title", is("Customer Not Found")))
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(
                        jsonPath("$.detail")
                                .value("Customer with Id '%d' not found".formatted(customerId)));
    }

    @Test
    void shouldFindCustomerByName() throws Exception {
        Customer customer = customerList.get(0);
        String customerName = customer.getName();

        this.mockMvc
                .perform(get("/api/customers/name/{name}", customerName))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(customer.getId()), Long.class))
                .andExpect(jsonPath("$.name", is(customer.getName())))
                .andExpect(jsonPath("$.email", is(customer.getEmail())))
                .andExpect(jsonPath("$.address", is(customer.getAddress())))
                .andExpect(jsonPath("$.amountAvailable", is(customer.getAmountAvailable())));
    }

    @Test
    void shouldCreateNewCustomer() throws Exception {
        CustomerRequest customerRequest =
                new CustomerRequest(
                        "New Customer", "first@customerRequest.email", "First Address", 0);
        this.mockMvc
                .perform(
                        post("/api/customers")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(customerRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue(Long.class)))
                .andExpect(jsonPath("$.name", is(customerRequest.name())))
                .andExpect(jsonPath("$.email", is(customerRequest.email())))
                .andExpect(jsonPath("$.address", is(customerRequest.address())))
                .andExpect(jsonPath("$.amountAvailable", is(customerRequest.amountAvailable())));
    }

    @Test
    void shouldReturn400WhenCreateNewCustomerWithoutNameAndEmail() throws Exception {
        Customer customer = new Customer(null, null, null, null, 0, 0);

        this.mockMvc
                .perform(
                        post("/api/customers")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(customer)))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", is("application/problem+json")))
                .andExpect(
                        jsonPath(
                                "$.type",
                                is("https://zalando.github.io/problem/constraint-violation")))
                .andExpect(jsonPath("$.title", is("Constraint Violation")))
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.violations", hasSize(2)))
                .andExpect(jsonPath("$.violations[0].field", is("email")))
                .andExpect(jsonPath("$.violations[0].message", is("Email cannot be empty")))
                .andExpect(jsonPath("$.violations[1].field", is("name")))
                .andExpect(jsonPath("$.violations[1].message", is("Name cannot be empty")))
                .andReturn();
    }

    @Test
    void shouldUpdateCustomer() throws Exception {
        Long customerId = customerList.get(0).getId();
        CustomerRequest customerRequest =
                new CustomerRequest("Updated text", "first@customer.email", "First Address", 100);

        this.mockMvc
                .perform(
                        put("/api/customers/{id}", customerId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(customerRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is(customerRequest.name())))
                .andExpect(jsonPath("$.email", is(customerRequest.email())))
                .andExpect(jsonPath("$.address", is(customerRequest.address())))
                .andExpect(jsonPath("$.amountAvailable", is(customerRequest.amountAvailable())));
    }

    @Test
    void shouldReturn404WhenUpdatingNonExistingCustomer() throws Exception {
        long customerId = customerList.get(0).getId() + 99_999;
        CustomerRequest customerRequest =
                new CustomerRequest("Updated text", "first@customer.email", "First Address", 0);

        this.mockMvc
                .perform(
                        put("/api/customers/{id}", customerId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(customerRequest)))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Content-Type", is("application/problem+json")))
                .andExpect(jsonPath("$.type", is("https://api.customers.com/errors/not-found")))
                .andExpect(jsonPath("$.title", is("Customer Not Found")))
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(
                        jsonPath("$.detail")
                                .value("Customer with Id '%d' not found".formatted(customerId)));
    }

    @Test
    void shouldDeleteCustomer() throws Exception {
        Customer customer = customerList.get(0);

        this.mockMvc
                .perform(delete("/api/customers/{id}", customer.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is(customer.getName())))
                .andExpect(jsonPath("$.email", is(customer.getEmail())))
                .andExpect(jsonPath("$.address", is(customer.getAddress())))
                .andExpect(jsonPath("$.amountAvailable", is(customer.getAmountAvailable())));
    }

    @Test
    void shouldReturn404WhenDeletingNonExistingCustomer() throws Exception {
        long customerId = customerList.get(0).getId() + 99_999;
        this.mockMvc
                .perform(delete("/api/customers/{id}", customerId))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Content-Type", is("application/problem+json")))
                .andExpect(jsonPath("$.type", is("https://api.customers.com/errors/not-found")))
                .andExpect(jsonPath("$.title", is("Customer Not Found")))
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(
                        jsonPath("$.detail")
                                .value("Customer with Id '%d' not found".formatted(customerId)));
    }
}
