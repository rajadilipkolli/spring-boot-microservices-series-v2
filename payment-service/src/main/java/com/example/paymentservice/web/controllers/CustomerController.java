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
