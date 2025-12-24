/*** Licensed under MIT License Copyright (c) 2023-2025 Raja Kolli. ***/
package com.example.paymentservice.web.controllers;

import static com.example.paymentservice.utils.AppConstants.PROFILE_TEST;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import com.example.paymentservice.exception.CustomerNotFoundException;
import com.example.paymentservice.model.query.FindCustomersQuery;
import com.example.paymentservice.model.request.CustomerRequest;
import com.example.paymentservice.model.response.CustomerResponse;
import com.example.paymentservice.model.response.PagedResult;
import com.example.paymentservice.services.CustomerService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(controllers = CustomerController.class)
@ActiveProfiles(PROFILE_TEST)
class CustomerControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private CustomerService customerService;

    @Autowired private ObjectMapper objectMapper;

    private List<Customer> customerList;

    @BeforeEach
    void setUp() {
        this.customerList =
                List.of(
                        new Customer()
                                .setId(1L)
                                .setName("First Customer")
                                .setEmail("first@customer.email")
                                .setAddress("First Address")
                                .setAmountAvailable(100)
                                .setAmountReserved(0),
                        new Customer()
                                .setId(2L)
                                .setName("Second Customer")
                                .setEmail("second@customer.email")
                                .setAddress("Second Address")
                                .setAmountAvailable(100)
                                .setAmountReserved(0),
                        new Customer()
                                .setId(3L)
                                .setName("Third Customer")
                                .setEmail("third@customer.email")
                                .setAddress("Third Address")
                                .setAmountAvailable(100)
                                .setAmountReserved(0));
    }

    @Test
    void shouldFetchAllCustomers() throws Exception {
        Page<Customer> page = new PageImpl<>(customerList);
        PagedResult<CustomerResponse> postPagedResult =
                new PagedResult<>(getCustomerResponseList(), page);
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
                                        customer.getPhone(),
                                        customer.getAddress(),
                                        customer.getAmountAvailable()))
                .toList();
    }

    @Nested
    @DisplayName("find methods")
    class Find {
        @Test
        void shouldFindCustomerById() throws Exception {
            Long customerId = 1L;
            CustomerResponse customerResponse =
                    new CustomerResponse(
                            customerId,
                            "text 1",
                            "junit@email.com",
                            "9876543210",
                            "junitAddress",
                            100);
            given(customerService.findCustomerById(customerId))
                    .willReturn(Optional.of(customerResponse));

            mockMvc.perform(get("/api/customers/{id}", customerId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name", is(customerResponse.name())));
        }

        @Test
        void shouldReturn404WhenFetchingNonExistingCustomerById() throws Exception {
            Long customerId = 1L;
            given(customerService.findCustomerById(customerId)).willReturn(Optional.empty());

            mockMvc.perform(get("/api/customers/{id}", customerId))
                    .andExpect(status().isNotFound())
                    .andExpect(
                            header().string(
                                            HttpHeaders.CONTENT_TYPE,
                                            is(MediaType.APPLICATION_PROBLEM_JSON_VALUE)))
                    .andExpect(
                            jsonPath(
                                    "$.type", is("https://api.microservices.com/errors/not-found")))
                    .andExpect(jsonPath("$.title", is("Customer Not Found")))
                    .andExpect(jsonPath("$.status", is(404)))
                    .andExpect(jsonPath("$.detail").value("Customer with Id '1' not found"));
        }

        @Test
        void shouldReturn404WhenFetchingNonExistingCustomerByName() throws Exception {
            String customerName = "junitCustomer";
            given(customerService.findCustomerByName(customerName))
                    .willThrow(new CustomerNotFoundException(customerName));

            mockMvc.perform(get("/api/customers/name/{name}", customerName))
                    .andExpect(status().isNotFound())
                    .andExpect(
                            header().string(
                                            HttpHeaders.CONTENT_TYPE,
                                            is(MediaType.APPLICATION_PROBLEM_JSON_VALUE)))
                    .andExpect(
                            jsonPath(
                                    "$.type", is("https://api.microservices.com/errors/not-found")))
                    .andExpect(jsonPath("$.title", is("Customer Not Found")))
                    .andExpect(jsonPath("$.status", is(404)))
                    .andExpect(
                            jsonPath("$.detail")
                                    .value("Customer with Name 'junitCustomer' not found"));
        }
    }

    @Nested
    @DisplayName("save methods")
    class Save {
        @Test
        void shouldCreateNewCustomer() throws Exception {

            CustomerRequest customerRequest =
                    new CustomerRequest(
                            "junitName", "email@junit.com", "1234567890", "junitAddress", 10);
            CustomerResponse customerResponse =
                    new CustomerResponse(
                            1L, "junitName", "email@junit.com", "9876543210", "junitAddress", 10);
            given(customerService.saveCustomer(any(CustomerRequest.class)))
                    .willReturn(customerResponse);
            mockMvc.perform(
                            post("/api/customers")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(customerRequest)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name", is(customerRequest.name())));
        }

        @Test
        void shouldReturn400WhenCreateNewCustomerWithoutNameAndEmail() throws Exception {
            CustomerRequest customerRequest = new CustomerRequest(null, null, null, null, 1);

            mockMvc.perform(
                            post("/api/customers")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(customerRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(
                            header().string(
                                            HttpHeaders.CONTENT_TYPE,
                                            is(MediaType.APPLICATION_PROBLEM_JSON_VALUE)))
                    .andExpect(
                            jsonPath(
                                    "$.type",
                                    is("https://api.microservices.com/errors/validation-error")))
                    .andExpect(jsonPath("$.title", is("Constraint Violation")))
                    .andExpect(jsonPath("$.status", is(400)))
                    .andExpect(jsonPath("$.violations", hasSize(3)))
                    .andExpect(jsonPath("$.violations[0].field", is("email")))
                    .andExpect(jsonPath("$.violations[0].message", is("Email cannot be Blank")))
                    .andExpect(jsonPath("$.violations[1].field", is("name")))
                    .andExpect(jsonPath("$.violations[1].message", is("Name cannot be Blank")))
                    .andReturn();
        }
    }

    @Nested
    @DisplayName("update methods")
    class Update {
        @Test
        void shouldUpdateCustomer() throws Exception {

            CustomerRequest customerRequest =
                    new CustomerRequest(
                            "customerUpdatedName",
                            "junitEmail@email.com",
                            "1234567890",
                            "junitAddress",
                            100);

            given(customerService.updateCustomer(eq(1L), any(CustomerRequest.class)))
                    .willReturn(
                            new CustomerResponse(
                                    1L,
                                    "customerUpdatedName",
                                    "junitEmail@email.com",
                                    "9876543210",
                                    "junitAddress",
                                    100));

            mockMvc.perform(
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
                            "customerUpdatedName",
                            "junitEmail@email.com",
                            "1234567890",
                            "junitAddress",
                            100);
            given(customerService.updateCustomer(eq(customerId), any(CustomerRequest.class)))
                    .willThrow(new CustomerNotFoundException(customerId));

            mockMvc.perform(
                            put("/api/customers/{id}", customerId)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(customerRequest)))
                    .andExpect(status().isNotFound())
                    .andExpect(
                            header().string(
                                            HttpHeaders.CONTENT_TYPE,
                                            is(MediaType.APPLICATION_PROBLEM_JSON_VALUE)))
                    .andExpect(
                            jsonPath(
                                    "$.type", is("https://api.microservices.com/errors/not-found")))
                    .andExpect(jsonPath("$.title", is("Customer Not Found")))
                    .andExpect(jsonPath("$.status", is(404)))
                    .andExpect(jsonPath("$.detail").value("Customer with Id '1' not found"));
        }
    }

    @Nested
    @DisplayName("delete methods")
    class Delete {
        @Test
        void shouldDeleteCustomer() throws Exception {
            Long customerId = 1L;
            CustomerResponse customer =
                    new CustomerResponse(
                            customerId,
                            "Some text",
                            "junit@email.com",
                            "9876543210",
                            "junitAddress",
                            0);
            given(customerService.findCustomerById(customerId)).willReturn(Optional.of(customer));
            doNothing().when(customerService).deleteCustomerById(customerId);

            mockMvc.perform(delete("/api/customers/{id}", customerId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name", is(customer.name())));
        }

        @Test
        void shouldReturn404WhenDeletingNonExistingCustomer() throws Exception {
            Long customerId = 1L;
            given(customerService.findCustomerById(customerId)).willReturn(Optional.empty());

            mockMvc.perform(delete("/api/customers/{id}", customerId))
                    .andExpect(status().isNotFound())
                    .andExpect(
                            header().string(
                                            HttpHeaders.CONTENT_TYPE,
                                            is(MediaType.APPLICATION_PROBLEM_JSON_VALUE)))
                    .andExpect(
                            jsonPath(
                                    "$.type", is("https://api.microservices.com/errors/not-found")))
                    .andExpect(jsonPath("$.title", is("Customer Not Found")))
                    .andExpect(jsonPath("$.status", is(404)))
                    .andExpect(jsonPath("$.detail").value("Customer with Id '1' not found"));
        }
    }
}
