/*** Licensed under MIT License Copyright (c) 2023 Raja Kolli. ***/
package com.example.paymentservice.web.controllers;

import static com.example.paymentservice.utils.AppConstants.PROFILE_TEST;
import static org.hamcrest.CoreMatchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.paymentservice.entities.Customer;
import com.example.paymentservice.exception.CustomerNotFoundException;
import com.example.paymentservice.model.query.FindCustomersQuery;
import com.example.paymentservice.model.request.CustomerRequest;
import com.example.paymentservice.model.response.CustomerResponse;
import com.example.paymentservice.model.response.PagedResult;
import com.example.paymentservice.services.CustomerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.zalando.problem.jackson.ProblemModule;
import org.zalando.problem.violations.ConstraintViolationProblemModule;

@WebMvcTest(controllers = CustomerController.class)
@ActiveProfiles(PROFILE_TEST)
class CustomerControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private CustomerService customerService;

    @Autowired private ObjectMapper objectMapper;

    private List<Customer> customerList;

    @BeforeEach
    void setUp() {
        this.customerList = new ArrayList<>();
        this.customerList.add(
                new Customer(1L, "text 1", "first@customer.email", "First Address", 0, 0));
        this.customerList.add(
                new Customer(2L, "text 2", "second@customer.email", "Second Address", 0, 0));
        this.customerList.add(
                new Customer(3L, "text 3", "third@customer.email", "Third Address", 0, 0));

        objectMapper.registerModule(new ProblemModule());
        objectMapper.registerModule(new ConstraintViolationProblemModule());
    }

    @Test
    void shouldFetchAllCustomers() throws Exception {
        Page<CustomerResponse> page = new PageImpl<>(getCustomerResponseList());
        PagedResult<CustomerResponse> postPagedResult = new PagedResult<>(page);
        FindCustomersQuery findCustomersQuery = new FindCustomersQuery(0, 10, "id", "desc");
        given(customerService.findAllCustomers(findCustomersQuery)).willReturn(postPagedResult);

        this.mockMvc
                .perform(get("/api/customers?pageNo=0&sortDir=desc"))
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

    private List<CustomerResponse> getCustomerResponseList() {
        return customerList.stream()
                .map(
                        customer ->
                                new CustomerResponse(
                                        customer.getId(),
                                        customer.getName(),
                                        customer.getEmail(),
                                        customer.getAddress(),
                                        customer.getAmountAvailable()))
                .toList();
    }

    @Test
    void shouldReturn404WhenFetchingNonExistingCustomer() throws Exception {
        String customerName = "junitCustomer";
        given(customerService.findCustomerByName(customerName))
                .willThrow(new CustomerNotFoundException(customerName));

        this.mockMvc
                .perform(get("/api/customers/name/{name}", customerName))
                .andExpect(status().isNotFound())
                .andExpect(
                        header().string(
                                        "Content-Type",
                                        is(MediaType.APPLICATION_PROBLEM_JSON_VALUE)))
                .andExpect(jsonPath("$.type", is("https://api.customers.com/errors/not-found")))
                .andExpect(jsonPath("$.title", is("Customer Not Found")))
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(
                        jsonPath("$.detail").value("Customer with Name 'junitCustomer' not found"));
    }

    @Test
    void shouldUpdateCustomer() throws Exception {

        CustomerRequest customerRequest =
                new CustomerRequest(
                        "customerUpdatedName", "junitEmail@email.com", "junitAddress", 100);

        given(customerService.updateCustomer(eq(1L), any(CustomerRequest.class)))
                .willReturn(
                        new CustomerResponse(
                                1L,
                                "customerUpdatedName",
                                "junitEmail@email.com",
                                "junitAddress",
                                100));

        this.mockMvc
                .perform(
                        put("/api/customers/{id}", 1L)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(customerRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId", is(1L), Long.class));
    }

    @Test
    void shouldReturn404WhenUpdatingNonExistingCustomer() throws Exception {
        Long customerId = 1L;
        CustomerRequest customerRequest =
                new CustomerRequest(
                        "customerUpdatedName", "junitEmail@email.com", "junitAddress", 100);
        given(customerService.updateCustomer(eq(customerId), any(CustomerRequest.class)))
                .willThrow(new CustomerNotFoundException(customerId));

        this.mockMvc
                .perform(
                        put("/api/customers/{id}", customerId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(customerRequest)))
                .andExpect(
                        header().string(
                                        "Content-Type",
                                        is(MediaType.APPLICATION_PROBLEM_JSON_VALUE)))
                .andExpect(jsonPath("$.type", is("https://api.customers.com/errors/not-found")))
                .andExpect(jsonPath("$.title", is("Customer Not Found")))
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.detail").value("Customer with Id '1' not found"));
    }
}
