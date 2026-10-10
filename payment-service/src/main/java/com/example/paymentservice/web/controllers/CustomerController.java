/*** Licensed under MIT License Copyright (c) 2021-2026 Raja Kolli. ***/
package com.example.paymentservice.web.controllers;

import com.example.paymentservice.config.logging.Loggable;
import com.example.paymentservice.exception.CustomerNotFoundException;
import com.example.paymentservice.model.query.FindCustomersQuery;
import com.example.paymentservice.model.request.CustomerRequest;
import com.example.paymentservice.model.response.CustomerResponse;
import com.example.paymentservice.model.response.OrderResponse;
import com.example.paymentservice.model.response.PagedResult;
import com.example.paymentservice.services.CustomerService;
import com.example.paymentservice.services.OrderProxyService;
import com.example.paymentservice.utils.AppConstants;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/customers")
@Loggable
class CustomerController {

    private final CustomerService customerService;
    private final OrderProxyService orderProxyService;

    /** Creates the controller with services for customer lookup and order retrieval. */
    CustomerController(CustomerService customerService, OrderProxyService orderProxyService) {
        this.customerService = customerService;
        this.orderProxyService = orderProxyService;
    }

    @GetMapping
    PagedResult<CustomerResponse> getAllCustomers(
            @RequestParam(defaultValue = AppConstants.DEFAULT_PAGE_NUMBER, required = false)
                    int pageNo,
            @RequestParam(defaultValue = AppConstants.DEFAULT_PAGE_SIZE, required = false)
                    int pageSize,
            @RequestParam(defaultValue = AppConstants.DEFAULT_SORT_BY, required = false)
                    String sortBy,
            @RequestParam(defaultValue = AppConstants.DEFAULT_SORT_DIRECTION, required = false)
                    String sortDir) {
        FindCustomersQuery findCustomersQuery =
                new FindCustomersQuery(pageNo, pageSize, sortBy, sortDir);
        return customerService.findAllCustomers(findCustomersQuery);
    }

    @GetMapping("/{id}")
    ResponseEntity<CustomerResponse> getCustomerById(@PathVariable Long id) {
        return customerService
                .findCustomerById(id)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new CustomerNotFoundException(id));
    }

    @GetMapping("/name/{name}")
    ResponseEntity<CustomerResponse> getCustomerByName(@PathVariable String name) {
        return customerService
                .findCustomerByName(name)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new CustomerNotFoundException(name));
    }

    @GetMapping("/by-email")
    ResponseEntity<CustomerResponse> getCustomerByEmail(@RequestParam String email) {
        return customerService
                .findCustomerByEmail(email)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> CustomerNotFoundException.forEmail(email));
    }

    /**
     * Saves the customer and returns HTTP 201 with its details and resource location.
     *
     * @param customerRequest the validated customer details
     * @return the customer response with its ID appended to the current request URI
     */
    @PostMapping
    ResponseEntity<CustomerResponse> createCustomer(
            @RequestBody @Valid CustomerRequest customerRequest) {
        CustomerResponse response = customerService.saveCustomer(customerRequest);
        URI location =
                ServletUriComponentsBuilder.fromCurrentRequest()
                        .path("/{id}")
                        .buildAndExpand(response.customerId())
                        .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @PutMapping("/{id}")
    ResponseEntity<CustomerResponse> updateCustomer(
            @PathVariable Long id, @RequestBody @Valid CustomerRequest customerRequest) {
        return ResponseEntity.ok(customerService.updateCustomer(id, customerRequest));
    }

    @DeleteMapping("/{id}")
    ResponseEntity<CustomerResponse> deleteCustomer(@PathVariable Long id) {
        return customerService
                .findCustomerById(id)
                .map(
                        customer -> {
                            customerService.deleteCustomerById(id);
                            return ResponseEntity.ok(customer);
                        })
                .orElseThrow(() -> new CustomerNotFoundException(id));
    }

    /**
     * Retrieves orders after validating pagination and confirming that the customer exists.
     *
     * @param pageNo page number starting at 1; defaults to 1 when omitted from the request
     * @param pageSize positive number of orders per page; defaults to 10 when omitted
     * @param sortBy order property to sort by; defaults to {@code id} when omitted
     * @param sortDir sort direction forwarded to the order service; defaults to {@code asc}
     * @return HTTP 200 with the order service's orders and pagination metadata
     * @throws IllegalArgumentException if {@code pageNo < 1} or {@code pageSize <= 0}
     * @throws CustomerNotFoundException if the customer does not exist
     * @throws org.springframework.web.client.RestClientResponseException if the order service
     *     returns an HTTP error
     * @throws org.springframework.web.client.ResourceAccessException if the order request fails
     *     due to an I/O error
     * @throws io.github.resilience4j.circuitbreaker.CallNotPermittedException if the order circuit
     *     breaker rejects the call
     */
    @GetMapping("/{id}/orders")
    ResponseEntity<PagedResult<OrderResponse>> getOrdersByCustomerId(
            @PathVariable Long id,
            @RequestParam(defaultValue = AppConstants.DEFAULT_PAGE_NUMBER, required = false)
                    int pageNo,
            @RequestParam(defaultValue = AppConstants.DEFAULT_PAGE_SIZE, required = false)
                    int pageSize,
            @RequestParam(defaultValue = AppConstants.DEFAULT_SORT_BY, required = false)
                    String sortBy,
            @RequestParam(defaultValue = AppConstants.DEFAULT_SORT_DIRECTION, required = false)
                    String sortDir) {
        if (pageNo < 1) {
            throw new IllegalArgumentException("pageNo must be greater than or equal to 1");
        }
        if (pageSize <= 0) {
            throw new IllegalArgumentException("pageSize must be greater than 0");
        }
        return customerService
                .findCustomerById(id)
                .map(
                        customer ->
                                ResponseEntity.ok(
                                        orderProxyService.getOrdersByCustomerId(
                                                id, pageNo, pageSize, sortBy, sortDir)))
                .orElseThrow(() -> new CustomerNotFoundException(id));
    }
}
