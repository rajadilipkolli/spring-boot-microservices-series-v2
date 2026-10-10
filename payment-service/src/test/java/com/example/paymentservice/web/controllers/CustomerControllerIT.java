/*** Licensed under MIT License Copyright (c) 2021-2026 Raja Kolli. ***/
package com.example.paymentservice.web.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
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
import com.example.paymentservice.model.response.CustomerResponse;
import com.example.paymentservice.model.response.OrderResponse;
import com.example.paymentservice.model.response.PagedResult;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpServerErrorException;

class CustomerControllerIT extends AbstractIntegrationTest {

    private List<Customer> customerList = null;

    /** Replaces existing customers with three fixtures containing separate address fields. */
    @BeforeEach
    void setUp() {
        customerRepository.deleteAll();

        customerList =
                List.of(
                        new Customer()
                                .setName("First Customer")
                                .setEmail("first@customer.email")
                                .setPhone("9876543210")
                                .setAddressLine1("First Address")
                                .setAddressLine2("First Address Line 2")
                                .setCity("First City")
                                .setState("First State")
                                .setZipCode("12345")
                                .setCountry("First Country")
                                .setAmountAvailable(BigDecimal.valueOf(100))
                                .setAmountReserved(BigDecimal.ZERO),
                        new Customer()
                                .setName("Second Customer")
                                .setEmail("second@customer.email")
                                .setPhone("9876543210")
                                .setAddressLine1("Second Address")
                                .setAddressLine2("Second Address Line 2")
                                .setCity("Second City")
                                .setState("Second State")
                                .setZipCode("12345")
                                .setCountry("Second Country")
                                .setAmountAvailable(BigDecimal.valueOf(100))
                                .setAmountReserved(BigDecimal.ZERO),
                        new Customer()
                                .setName("Third Customer")
                                .setEmail("third@customer.email")
                                .setPhone("9876543210")
                                .setAddressLine1("Third Address")
                                .setAddressLine2("Third Address Line 2")
                                .setCity("Third City")
                                .setState("Third State")
                                .setZipCode("12345")
                                .setCountry("Third Country")
                                .setAmountAvailable(BigDecimal.valueOf(100))
                                .setAmountReserved(BigDecimal.ZERO));
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

    /** Verifies that customer lookup by ID returns customer details with a string customer ID. */
    @Test
    void shouldFindCustomerById() throws Exception {
        Customer customer = customerList.getFirst();
        Long customerId = customer.getId();

        this.mockMvc
                .perform(get("/api/customers/{id}", customerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId", is(customer.getId().toString())))
                .andExpect(jsonPath("$.name", is(customer.getName())))
                .andExpect(jsonPath("$.email", is(customer.getEmail())))
                .andExpect(jsonPath("$.phone", is(customer.getPhone())))
                .andExpect(jsonPath("$.addressLine1", is(customer.getAddressLine1())))
                .andExpect(jsonPath("$.addressLine2", is(customer.getAddressLine2())))
                .andExpect(jsonPath("$.city", is(customer.getCity())))
                .andExpect(jsonPath("$.state", is(customer.getState())))
                .andExpect(jsonPath("$.zipCode", is(customer.getZipCode())))
                .andExpect(jsonPath("$.country", is(customer.getCountry())))
                .andExpect(
                        jsonPath(
                                "$.amountAvailable",
                                is(customer.getAmountAvailable().doubleValue())));
    }

    /** Verifies that lookup of an unknown customer ID returns HTTP 404 with problem details. */
    @Test
    void shouldReturn404WhenFetchingNonExistingCustomer() throws Exception {
        long customerId = customerList.getFirst().getId() + 99_999;
        this.mockMvc
                .perform(get("/api/customers/{id}", customerId))
                .andExpect(status().isNotFound())
                .andExpect(
                        header().string(
                                        HttpHeaders.CONTENT_TYPE,
                                        is(MediaType.APPLICATION_PROBLEM_JSON_VALUE)))
                .andExpect(jsonPath("$.type", is("https://api.microservices.com/errors/not-found")))
                .andExpect(jsonPath("$.title", is("Customer Not Found")))
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(
                        jsonPath("$.detail")
                                .value("Customer with Id '%d' not found".formatted(customerId)));
    }

    /** Verifies that customer lookup by name returns customer details with a string customer ID. */
    @Test
    void shouldFindCustomerByName() throws Exception {
        Customer customer = customerList.getFirst();
        String customerName = customer.getName();

        this.mockMvc
                .perform(get("/api/customers/name/{name}", customerName))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId", is(customer.getId().toString())))
                .andExpect(jsonPath("$.name", is(customer.getName())))
                .andExpect(jsonPath("$.email", is(customer.getEmail())))
                .andExpect(jsonPath("$.phone", is(customer.getPhone())))
                .andExpect(jsonPath("$.addressLine1", is(customer.getAddressLine1())))
                .andExpect(jsonPath("$.addressLine2", is(customer.getAddressLine2())))
                .andExpect(jsonPath("$.city", is(customer.getCity())))
                .andExpect(jsonPath("$.state", is(customer.getState())))
                .andExpect(jsonPath("$.zipCode", is(customer.getZipCode())))
                .andExpect(jsonPath("$.country", is(customer.getCountry())))
                .andExpect(
                        jsonPath(
                                "$.amountAvailable",
                                is(customer.getAmountAvailable().doubleValue())));
    }

    /**
     * Verifies that customer creation returns a location header, string ID, and the submitted
     * details.
     */
    @Test
    void shouldCreateNewCustomer() throws Exception {
        CustomerRequest customerRequest =
                new CustomerRequest(
                        "New Customer",
                        "firstnew@customerRequest.email",
                        "1234567890",
                        "First Address",
                        null,
                        "Hyderabad",
                        "Telangana",
                        "500081",
                        "India",
                        new BigDecimal("10000"));
        this.mockMvc
                .perform(
                        post("/api/customers")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(jsonMapper.writeValueAsString(customerRequest)))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.customerId").isString())
                .andExpect(jsonPath("$.name", is(customerRequest.name())))
                .andExpect(jsonPath("$.email", is(customerRequest.email().toLowerCase())))
                .andExpect(jsonPath("$.phone", is(customerRequest.phone())))
                .andExpect(jsonPath("$.addressLine1", is("First Address")))
                .andExpect(jsonPath("$.addressLine2", is(nullValue())))
                .andExpect(jsonPath("$.city", is("Hyderabad")))
                .andExpect(jsonPath("$.state", is("Telangana")))
                .andExpect(jsonPath("$.zipCode", is("500081")))
                .andExpect(jsonPath("$.country", is("India")))
                .andExpect(jsonPath("$.amountAvailable", is(10000.0)));
    }

    /**
     * Verifies that email lookup ignores case and returns the persisted customer details.
     *
     * @param email a case variant of the stored customer email
     */
    @ParameterizedTest
    @ValueSource(strings = {"first@customer.email", "FIRST@CUSTOMER.EMAIL", "FiRsT@CuStOmEr.EmAiL"})
    void shouldFindCustomerByEmail(String email) {
        Customer customer = customerList.getFirst(); // first@customer.email

        this.mockMvcTester
                .get()
                .uri("/api/customers/by-email")
                .param("email", email)
                .assertThat()
                .hasStatusOk()
                .hasContentType(MediaType.APPLICATION_JSON)
                .bodyJson()
                .convertTo(CustomerResponse.class)
                .satisfies(
                        customerResponse -> {
                            assertThat(customerResponse.customerId()).isEqualTo(customer.getId());
                            assertThat(customerResponse.name()).isEqualTo(customer.getName());
                            assertThat(customerResponse.email()).isEqualTo(customer.getEmail());
                            assertThat(customerResponse.phone()).isEqualTo(customer.getPhone());
                            assertThat(customerResponse.addressLine1())
                                    .isEqualTo(customer.getAddressLine1());
                            assertThat(customerResponse.amountAvailable())
                                    .isEqualTo(customer.getAmountAvailable());
                        });
    }

    /**
     * Verifies that creating an existing customer succeeds and returns its details with a string
     * ID.
     */
    @Test
    void shouldReturnWithNoErrorCreatingExistingCustomer() throws Exception {
        Customer customer = customerList.getFirst();
        CustomerRequest customerRequest =
                new CustomerRequest(
                        customer.getName(),
                        customer.getEmail(),
                        customer.getPhone(),
                        customer.getAddressLine1(),
                        customer.getAddressLine2(),
                        customer.getCity(),
                        customer.getState(),
                        customer.getZipCode(),
                        customer.getCountry(),
                        customer.getAmountAvailable());
        this.mockMvc
                .perform(
                        post("/api/customers")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(jsonMapper.writeValueAsString(customerRequest)))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.customerId").isString())
                .andExpect(jsonPath("$.name", is(customerRequest.name())))
                .andExpect(jsonPath("$.email", is(customerRequest.email())))
                .andExpect(jsonPath("$.phone", is(customerRequest.phone())))
                .andExpect(jsonPath("$.addressLine1", is(customer.getAddressLine1())))
                .andExpect(jsonPath("$.addressLine2", is(customer.getAddressLine2())))
                .andExpect(jsonPath("$.city", is(customer.getCity())))
                .andExpect(jsonPath("$.state", is(customer.getState())))
                .andExpect(jsonPath("$.zipCode", is(customer.getZipCode())))
                .andExpect(jsonPath("$.country", is(customer.getCountry())))
                .andExpect(
                        jsonPath(
                                "$.amountAvailable",
                                is(customerRequest.amountAvailable().doubleValue())));
    }

    /** Verifies that missing required customer details produce HTTP 400 validation errors. */
    @Test
    void shouldReturn400WhenCreateNewCustomerWithoutNameAndEmail() throws Exception {
        CustomerRequest customer =
                new CustomerRequest(
                        null, null, null, null, null, null, null, null, null, BigDecimal.ZERO);

        this.mockMvc
                .perform(
                        post("/api/customers")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(jsonMapper.writeValueAsString(customer)))
                .andExpect(status().isBadRequest())
                .andExpect(
                        header().string(
                                        "Content-Type",
                                        is(MediaType.APPLICATION_PROBLEM_JSON_VALUE)))
                .andExpect(
                        jsonPath(
                                "$.type",
                                is("https://api.microservices.com/errors/validation-error")))
                .andExpect(jsonPath("$.title", is("Constraint Violation")))
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.violations", hasSize(9)))
                .andExpect(jsonPath("$.violations[1].field", is("amountAvailable")))
                .andExpect(
                        jsonPath(
                                "$.violations[1].message",
                                is("AmountAvailable must be greater than 0")))
                .andExpect(jsonPath("$.violations[0].field", is("addressLine1")))
                .andExpect(
                        jsonPath("$.violations[0].message", is("Address Line 1 cannot be Blank")))
                .andExpect(jsonPath("$.violations[2].field", is("city")))
                .andExpect(jsonPath("$.violations[2].message", is("City cannot be Blank")))
                .andExpect(jsonPath("$.violations[3].field", is("country")))
                .andExpect(jsonPath("$.violations[3].message", is("Country cannot be Blank")))
                .andExpect(jsonPath("$.violations[4].field", is("email")))
                .andExpect(jsonPath("$.violations[4].message", is("Email cannot be Blank")))
                .andExpect(jsonPath("$.violations[5].field", is("name")))
                .andExpect(jsonPath("$.violations[5].message", is("Name cannot be Blank")))
                .andExpect(jsonPath("$.violations[6].field", is("phone")))
                .andExpect(
                        jsonPath(
                                "$.violations[6].message", is("Customer Phone number is required")))
                .andExpect(jsonPath("$.violations[7].field", is("state")))
                .andExpect(jsonPath("$.violations[7].message", is("State cannot be Blank")))
                .andExpect(jsonPath("$.violations[8].field", is("zipCode")))
                .andExpect(jsonPath("$.violations[8].message", is("Zip Code cannot be Blank")))
                .andReturn();
    }

    /** Verifies that updating a customer returns the customer ID as a JSON string. */
    @Test
    void shouldUpdateCustomer() throws Exception {
        Long customerId = customerList.getFirst().getId();
        CustomerRequest customerRequest =
                new CustomerRequest(
                        "Updated text",
                        "first@customer.email",
                        "1234567890",
                        "First Address",
                        null,
                        "Hyderabad",
                        "Telangana",
                        "500081",
                        "India",
                        new BigDecimal("500"));

        this.mockMvc
                .perform(
                        put("/api/customers/{id}", customerId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(jsonMapper.writeValueAsString(customerRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(customerId.toString()))
                .andExpect(jsonPath("$.name", is(customerRequest.name())))
                .andExpect(jsonPath("$.email", is(customerRequest.email())))
                .andExpect(jsonPath("$.addressLine1", is(customerRequest.addressLine1())))
                .andExpect(jsonPath("$.addressLine2", is(customerRequest.addressLine2())))
                .andExpect(jsonPath("$.city", is(customerRequest.city())))
                .andExpect(jsonPath("$.state", is(customerRequest.state())))
                .andExpect(jsonPath("$.zipCode", is(customerRequest.zipCode())))
                .andExpect(jsonPath("$.country", is(customerRequest.country())))
                .andExpect(jsonPath("$.phone", is(customerRequest.phone())))
                .andExpect(jsonPath("$.amountAvailable", is(500.0)));
    }

    /** Verifies that updating an unknown customer returns HTTP 404. */
    @Test
    void shouldReturn404WhenUpdatingNonExistingCustomer() throws Exception {
        long customerId = customerList.getFirst().getId() + 99_999;
        CustomerRequest customerRequest =
                new CustomerRequest(
                        "Updated text",
                        "first@customer.email",
                        "1234567890",
                        "First Address",
                        null,
                        "Hyderabad",
                        "Telangana",
                        "500081",
                        "India",
                        new BigDecimal("10000"));

        this.mockMvc
                .perform(
                        put("/api/customers/{id}", customerId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(jsonMapper.writeValueAsString(customerRequest)))
                .andExpect(status().isNotFound())
                .andExpect(
                        header().string(
                                        "Content-Type",
                                        is(MediaType.APPLICATION_PROBLEM_JSON_VALUE)))
                .andExpect(jsonPath("$.type", is("https://api.microservices.com/errors/not-found")))
                .andExpect(jsonPath("$.title", is("Customer Not Found")))
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(
                        jsonPath("$.detail")
                                .value("Customer with Id '%d' not found".formatted(customerId)));
    }

    @Test
    void shouldReturn400WhenAmountHasTooManyDecimalPlaces() throws Exception {
        CustomerRequest customerRequest =
                new CustomerRequest(
                        "New Customer",
                        "firstnew@customerRequest.email",
                        "1234567890",
                        "First Address",
                        null,
                        "Hyderabad",
                        "Telangana",
                        "500081",
                        "India",
                        new BigDecimal("10.005"));

        this.mockMvc
                .perform(
                        post("/api/customers")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(jsonMapper.writeValueAsString(customerRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(
                        header().string(
                                        "Content-Type",
                                        is(MediaType.APPLICATION_PROBLEM_JSON_VALUE)))
                .andExpect(jsonPath("$.title", is("Constraint Violation")))
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.violations[0].field", is("amountAvailable")))
                .andExpect(
                        jsonPath(
                                "$.violations[0].message",
                                is("AmountAvailable can have at most 2 decimal places")));
    }

    /** Verifies that deleting a customer returns the deleted details with a string customer ID. */
    @Test
    void shouldDeleteCustomer() throws Exception {
        Customer customer = customerList.getFirst();

        this.mockMvc
                .perform(delete("/api/customers/{id}", customer.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(customer.getId().toString()))
                .andExpect(jsonPath("$.name", is(customer.getName())))
                .andExpect(jsonPath("$.email", is(customer.getEmail())))
                .andExpect(jsonPath("$.addressLine1", is(customer.getAddressLine1())))
                .andExpect(jsonPath("$.addressLine2", is(customer.getAddressLine2())))
                .andExpect(jsonPath("$.city", is(customer.getCity())))
                .andExpect(jsonPath("$.state", is(customer.getState())))
                .andExpect(jsonPath("$.zipCode", is(customer.getZipCode())))
                .andExpect(jsonPath("$.country", is(customer.getCountry())))
                .andExpect(jsonPath("$.phone", is(customer.getPhone())))
                .andExpect(
                        jsonPath(
                                "$.amountAvailable",
                                is(customer.getAmountAvailable().doubleValue())));
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
                .andExpect(jsonPath("$.type", is("https://api.microservices.com/errors/not-found")))
                .andExpect(jsonPath("$.title", is("Customer Not Found")))
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(
                        jsonPath("$.detail")
                                .value("Customer with Id '%d' not found".formatted(customerId)));
    }

    @Test
    void shouldGetOrdersByCustomerId() throws Exception {
        Customer customer = customerList.getFirst();
        Long customerId = customer.getId();

        // Stub the proxy
        OrderResponse.Address address =
                new OrderResponse.Address("Street", "Suite", "City", "State", "Zip", "Country");
        OrderResponse.OrderItemResponse item =
                new OrderResponse.OrderItemResponse(
                        100L, "P1", 2, new BigDecimal("10.0"), new BigDecimal("20.0"));
        OrderResponse orderResponse =
                new OrderResponse(
                        1L,
                        customerId,
                        "COMPLETED",
                        "PAYMENT",
                        address,
                        LocalDateTime.now(),
                        new BigDecimal("20.0"),
                        List.of(item));

        PagedResult<OrderResponse> pagedResult =
                new PagedResult<>(List.of(orderResponse), 1, 1, 1, true, true, false, false);

        given(orderServiceProxy.getOrdersByCustomerId(eq(customerId), eq(0), eq(10), eq("id,asc")))
                .willReturn(pagedResult);

        this.mockMvc
                .perform(get("/api/customers/{id}/orders", customerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.data[0].orderId", is("1")))
                .andExpect(jsonPath("$.data[0].customerId").value(customerId.toString()))
                .andExpect(jsonPath("$.data[0].status", is("COMPLETED")))
                .andExpect(jsonPath("$.data[0].items[0].itemId", is("100")))
                .andExpect(jsonPath("$.data[0].items[0].productPrice", is(10.0)))
                .andExpect(jsonPath("$.data[0].items[0].price", is(20.0)))
                .andExpect(jsonPath("$.data[0].deliveryAddress.addressLine1", is("Street")))
                .andExpect(jsonPath("$.data[0].deliveryAddress.addressLine2", is("Suite")))
                .andExpect(jsonPath("$.data[0].totalPrice", is(20.0)));
    }

    @Autowired private CircuitBreakerRegistry circuitBreakerRegistry;

    @Test
    void shouldReturnErrorWhenOrderServiceFails() throws Exception {
        Long customerId = customerList.getFirst().getId();
        given(orderServiceProxy.getOrdersByCustomerId(eq(customerId), eq(0), eq(10), eq("id,asc")))
                .willThrow(new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE));
        try {
            mockMvc.perform(get("/api/customers/{id}/orders", customerId))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.data").doesNotExist());
        } finally {
            circuitBreakerRegistry.circuitBreaker("default").reset();
        }
    }

    @Test
    void shouldReturnErrorWhenOrderCircuitIsOpen() throws Exception {
        var circuitBreaker = circuitBreakerRegistry.circuitBreaker("default");
        circuitBreaker.transitionToOpenState();
        try {
            mockMvc.perform(get("/api/customers/{id}/orders", customerList.getFirst().getId()))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.data").doesNotExist());
            verifyNoInteractions(orderServiceProxy);
        } finally {
            circuitBreaker.reset();
        }
    }
}
