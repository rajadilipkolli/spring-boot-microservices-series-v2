/*** Licensed under MIT License Copyright (c) 2021-2025 Raja Kolli. ***/
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
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class CustomerControllerIT extends AbstractIntegrationTest {

    private List<Customer> customerList = null;

    @BeforeEach
    void setUp() {
        customerRepository.deleteAll();

        customerList =
                List.of(
                        new Customer()
                                .setName("First Customer")
                                .setEmail("first@customer.email")
                                .setPhone("9876543210")
                                .setAddress("First Address")
                                .setAmountAvailable(100)
                                .setAmountReserved(0),
                        new Customer()
                                .setName("Second Customer")
                                .setEmail("second@customer.email")
                                .setPhone("9876543210")
                                .setAddress("Second Address")
                                .setAmountAvailable(100)
                                .setAmountReserved(0),
                        new Customer()
                                .setName("Third Customer")
                                .setEmail("third@customer.email")
                                .setPhone("9876543210")
                                .setAddress("Third Address")
                                .setAmountAvailable(100)
                                .setAmountReserved(0));
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
        Customer customer = customerList.getFirst();
        Long customerId = customer.getId();

        this.mockMvc
                .perform(get("/api/customers/{id}", customerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId", is(customer.getId()), Long.class))
                .andExpect(jsonPath("$.name", is(customer.getName())))
                .andExpect(jsonPath("$.email", is(customer.getEmail())))
                .andExpect(jsonPath("$.phone", is(customer.getPhone())))
                .andExpect(jsonPath("$.address", is(customer.getAddress())))
                .andExpect(jsonPath("$.amountAvailable", is(customer.getAmountAvailable())));
    }

    @Test
    void shouldReturn404WhenFetchingNonExistingCustomer() throws Exception {
        long customerId = customerList.getFirst().getId() + 99_999;
        this.mockMvc
                .perform(get("/api/customers/{id}", customerId))
                .andExpect(status().isNotFound())
                .andExpect(
                        header().string(
                                        "Content-Type",
                                        is(MediaType.APPLICATION_PROBLEM_JSON_VALUE)))
                .andExpect(jsonPath("$.type", is("https://api.customers.com/errors/not-found")))
                .andExpect(jsonPath("$.title", is("Customer Not Found")))
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(
                        jsonPath("$.detail")
                                .value("Customer with Id '%d' not found".formatted(customerId)));
    }

    @Test
    void shouldFindCustomerByName() throws Exception {
        Customer customer = customerList.getFirst();
        String customerName = customer.getName();

        this.mockMvc
                .perform(get("/api/customers/name/{name}", customerName))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId", is(customer.getId()), Long.class))
                .andExpect(jsonPath("$.name", is(customer.getName())))
                .andExpect(jsonPath("$.email", is(customer.getEmail())))
                .andExpect(jsonPath("$.phone", is(customer.getPhone())))
                .andExpect(jsonPath("$.address", is(customer.getAddress())))
                .andExpect(jsonPath("$.amountAvailable", is(customer.getAmountAvailable())));
    }

    @Test
    void shouldCreateNewCustomer() throws Exception {
        CustomerRequest customerRequest =
                new CustomerRequest(
                        "New Customer",
                        "firstnew@customerRequest.email",
                        "1234567890",
                        "First Address",
                        10_000);
        this.mockMvc
                .perform(
                        post("/api/customers")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(customerRequest)))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.customerId", notNullValue(Long.class)))
                .andExpect(jsonPath("$.name", is(customerRequest.name())))
                .andExpect(jsonPath("$.email", is(customerRequest.email())))
                .andExpect(jsonPath("$.phone", is(customerRequest.phone())))
                .andExpect(jsonPath("$.address", is(customerRequest.address())))
                .andExpect(jsonPath("$.amountAvailable", is(customerRequest.amountAvailable())));
    }

    @Test
    void shouldReturnWithNoErrorCreatingExistingCustomer() throws Exception {
        Customer customer = customerList.getFirst();
        CustomerRequest customerRequest =
                new CustomerRequest(
                        customer.getName(),
                        customer.getEmail(),
                        customer.getPhone(),
                        customer.getAddress(),
                        customer.getAmountAvailable());
        this.mockMvc
                .perform(
                        post("/api/customers")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(customerRequest)))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.customerId", notNullValue(Long.class)))
                .andExpect(jsonPath("$.name", is(customerRequest.name())))
                .andExpect(jsonPath("$.email", is(customerRequest.email())))
                .andExpect(jsonPath("$.phone", is(customerRequest.phone())))
                .andExpect(jsonPath("$.address", is(customerRequest.address())))
                .andExpect(jsonPath("$.amountAvailable", is(customerRequest.amountAvailable())));
    }

    @Test
    void shouldReturn400WhenCreateNewCustomerWithoutNameAndEmail() throws Exception {
        CustomerRequest customer = new CustomerRequest(null, null, null, null, 0);

        this.mockMvc
                .perform(
                        post("/api/customers")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(customer)))
                .andExpect(status().isBadRequest())
                .andExpect(
                        header().string(
                                        "Content-Type",
                                        is(MediaType.APPLICATION_PROBLEM_JSON_VALUE)))
                .andExpect(
                        jsonPath(
                                "$.type",
                                is("https://zalando.github.io/problem/constraint-violation")))
                .andExpect(jsonPath("$.title", is("Constraint Violation")))
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.violations", hasSize(4)))
                .andExpect(jsonPath("$.violations[0].field", is("amountAvailable")))
                .andExpect(
                        jsonPath(
                                "$.violations[0].message",
                                is("AmountAvailable must be greater than 0")))
                .andExpect(jsonPath("$.violations[1].field", is("email")))
                .andExpect(jsonPath("$.violations[1].message", is("Email cannot be Blank")))
                .andExpect(jsonPath("$.violations[2].field", is("name")))
                .andExpect(jsonPath("$.violations[2].message", is("Name cannot be Blank")))
                .andReturn();
    }

    @Test
    void shouldUpdateCustomer() throws Exception {
        Long customerId = customerList.getFirst().getId();
        CustomerRequest customerRequest =
                new CustomerRequest(
                        "Updated text", "first@customer.email", "1234567890", "First Address", 500);

        this.mockMvc
                .perform(
                        put("/api/customers/{id}", customerId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(customerRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(customerId))
                .andExpect(jsonPath("$.name", is(customerRequest.name())))
                .andExpect(jsonPath("$.email", is(customerRequest.email())))
                .andExpect(jsonPath("$.address", is(customerRequest.address())))
                .andExpect(jsonPath("$.phone", is(customerRequest.phone())))
                .andExpect(jsonPath("$.amountAvailable", is(customerRequest.amountAvailable())));
    }

    @Test
    void shouldReturn404WhenUpdatingNonExistingCustomer() throws Exception {
        long customerId = customerList.getFirst().getId() + 99_999;
        CustomerRequest customerRequest =
                new CustomerRequest(
                        "Updated text",
                        "first@customer.email",
                        "1234567890",
                        "First Address",
                        10_000);

        this.mockMvc
                .perform(
                        put("/api/customers/{id}", customerId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(customerRequest)))
                .andExpect(status().isNotFound())
                .andExpect(
                        header().string(
                                        "Content-Type",
                                        is(MediaType.APPLICATION_PROBLEM_JSON_VALUE)))
                .andExpect(jsonPath("$.type", is("https://api.customers.com/errors/not-found")))
                .andExpect(jsonPath("$.title", is("Customer Not Found")))
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(
                        jsonPath("$.detail")
                                .value("Customer with Id '%d' not found".formatted(customerId)));
    }

    @Test
    void shouldDeleteCustomer() throws Exception {
        Customer customer = customerList.getFirst();

        this.mockMvc
                .perform(delete("/api/customers/{id}", customer.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(customer.getId()))
                .andExpect(jsonPath("$.name", is(customer.getName())))
                .andExpect(jsonPath("$.email", is(customer.getEmail())))
                .andExpect(jsonPath("$.address", is(customer.getAddress())))
                .andExpect(jsonPath("$.phone", is(customer.getPhone())))
                .andExpect(jsonPath("$.amountAvailable", is(customer.getAmountAvailable())));
    }

    @Test
    void shouldReturn404WhenDeletingNonExistingCustomer() throws Exception {
        long customerId = customerList.getFirst().getId() + 99_999;
        this.mockMvc
                .perform(delete("/api/customers/{id}", customerId))
                .andExpect(status().isNotFound())
                .andExpect(
                        header().string(
                                        "Content-Type",
                                        is(MediaType.APPLICATION_PROBLEM_JSON_VALUE)))
                .andExpect(jsonPath("$.type", is("https://api.customers.com/errors/not-found")))
                .andExpect(jsonPath("$.title", is("Customer Not Found")))
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(
                        jsonPath("$.detail")
                                .value("Customer with Id '%d' not found".formatted(customerId)));
    }
}
