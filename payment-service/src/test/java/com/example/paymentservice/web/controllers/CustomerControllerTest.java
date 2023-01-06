/* Licensed under Apache-2.0 2021-2022 */
package com.example.paymentservice.web.controllers;

import static com.example.paymentservice.utils.AppConstants.PROFILE_TEST;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.paymentservice.entities.Customer;
import com.example.paymentservice.model.response.PagedResult;
import com.example.paymentservice.services.CustomerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
                new Customer(
                        1L,
                        "text 1",
                        "first@customer.email",
                        "First Address",
                        0,
                        0,
                        new ArrayList<>()));
        this.customerList.add(
                new Customer(
                        2L,
                        "text 2",
                        "second@customer.email",
                        "Second Address",
                        0,
                        0,
                        new ArrayList<>()));
        this.customerList.add(
                new Customer(
                        3L,
                        "text 3",
                        "third@customer.email",
                        "Third Address",
                        0,
                        0,
                        new ArrayList<>()));
    }

    @Test
    void shouldFetchAllCustomers() throws Exception {
        Page<Customer> page = new PageImpl<>(customerList);
        PagedResult<Customer> postPagedResult = new PagedResult<>(page);
        given(customerService.findAllCustomers(0, 10, "id", "asc")).willReturn(postPagedResult);

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
        Long customerId = 1L;
        Customer customer =
                new Customer(
                        customerId,
                        "text 1",
                        "first@customer.email",
                        "First Address",
                        0,
                        0,
                        new ArrayList<>());
        given(customerService.findCustomerById(customerId)).willReturn(Optional.of(customer));

        this.mockMvc
                .perform(get("/api/customers/{id}", customerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is(customer.getName())));
    }

    @Test
    void shouldReturn404WhenFetchingNonExistingCustomer() throws Exception {
        Long customerId = 1L;
        given(customerService.findCustomerById(customerId)).willReturn(Optional.empty());

        this.mockMvc
                .perform(get("/api/customers/{id}", customerId))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldCreateNewCustomer() throws Exception {
        given(customerService.saveCustomer(any(Customer.class)))
                .willAnswer((invocation) -> invocation.getArgument(0));

        Customer customer =
                new Customer(
                        1L,
                        "some text",
                        "first@customer.email",
                        "First Address",
                        0,
                        0,
                        new ArrayList<>());
        this.mockMvc
                .perform(
                        post("/api/customers")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(customer)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.name", is(customer.getName())));
    }

    @Test
    void shouldReturn400WhenCreateNewCustomerWithoutEmail() throws Exception {
        Customer customer = new Customer(null, null, null, null, 0, 0, null);

        this.mockMvc
                .perform(
                        post("/api/customers")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(customer)))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", is("application/problem+json")))
                .andExpect(jsonPath("$.type", is("about:blank")))
                .andExpect(jsonPath("$.title", is("Bad Request")))
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.detail", is("Invalid request content.")))
                .andExpect(jsonPath("$.instance", is("/api/customers")))
                .andReturn();
    }

    @Test
    void shouldUpdateCustomer() throws Exception {
        Long customerId = 1L;
        Customer customer =
                new Customer(
                        customerId,
                        "Updated Name",
                        "first@customer.email",
                        "First Address",
                        0,
                        0,
                        new ArrayList<>());
        given(customerService.findCustomerById(customerId)).willReturn(Optional.of(customer));
        given(customerService.saveCustomer(any(Customer.class)))
                .willAnswer((invocation) -> invocation.getArgument(0));

        this.mockMvc
                .perform(
                        put("/api/customers/{id}", customer.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is(customer.getName())));
    }

    @Test
    void shouldReturn404WhenUpdatingNonExistingCustomer() throws Exception {
        Long customerId = 1L;
        given(customerService.findCustomerById(customerId)).willReturn(Optional.empty());
        Customer customer =
                new Customer(
                        customerId,
                        "Updated text",
                        "first@customer.email",
                        "First Address",
                        0,
                        0,
                        new ArrayList<>());

        this.mockMvc
                .perform(
                        put("/api/customers/{id}", customerId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(customer)))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldDeleteCustomer() throws Exception {
        Long customerId = 1L;
        Customer customer =
                new Customer(
                        customerId,
                        "Some text",
                        "first@customer.email",
                        "First Address",
                        0,
                        0,
                        new ArrayList<>());
        given(customerService.findCustomerById(customerId)).willReturn(Optional.of(customer));
        doNothing().when(customerService).deleteCustomerById(customer.getId());

        this.mockMvc
                .perform(delete("/api/customers/{id}", customer.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is(customer.getName())));
    }

    @Test
    void shouldReturn404WhenDeletingNonExistingCustomer() throws Exception {
        Long customerId = 1L;
        given(customerService.findCustomerById(customerId)).willReturn(Optional.empty());

        this.mockMvc
                .perform(delete("/api/customers/{id}", customerId))
                .andExpect(status().isNotFound());
    }
}
