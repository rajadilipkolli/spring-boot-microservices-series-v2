/*** Licensed under MIT License Copyright (c) 2021-2023 Raja Kolli. ***/
package com.example.paymentservice.web.controllers;

import com.example.paymentservice.config.logging.Loggable;
import com.example.paymentservice.exception.CustomerNotFoundException;
import com.example.paymentservice.model.query.FindCustomersQuery;
import com.example.paymentservice.model.request.CustomerRequest;
import com.example.paymentservice.model.response.CustomerResponse;
import com.example.paymentservice.model.response.PagedResult;
import com.example.paymentservice.services.CustomerService;
import com.example.paymentservice.utils.AppConstants;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
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

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/customers")
@Loggable
public class CustomerController {

    private final CustomerService customerService;

    @GetMapping
    public PagedResult<CustomerResponse> getAllCustomers(
            @RequestParam(
                            value = "pageNo",
                            defaultValue = AppConstants.DEFAULT_PAGE_NUMBER,
                            required = false)
                    int pageNo,
            @RequestParam(
                            value = "pageSize",
                            defaultValue = AppConstants.DEFAULT_PAGE_SIZE,
                            required = false)
                    int pageSize,
            @RequestParam(
                            value = "sortBy",
                            defaultValue = AppConstants.DEFAULT_SORT_BY,
                            required = false)
                    String sortBy,
            @RequestParam(
                            value = "sortDir",
                            defaultValue = AppConstants.DEFAULT_SORT_DIRECTION,
                            required = false)
                    String sortDir) {
        FindCustomersQuery findCustomersQuery =
                new FindCustomersQuery(pageNo, pageSize, sortBy, sortDir);
        return customerService.findAllCustomers(findCustomersQuery);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CustomerResponse> getCustomerById(@PathVariable Long id) {
        return customerService
                .findCustomerById(id)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new CustomerNotFoundException(id));
    }

    @GetMapping("/name/{name}")
    public ResponseEntity<CustomerResponse> getCustomerByName(@PathVariable String name) {
        return customerService
                .findCustomerByName(name)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new CustomerNotFoundException(name));
    }

    @PostMapping
    public ResponseEntity<CustomerResponse> createCustomer(
            @RequestBody @Valid CustomerRequest customerRequest) {
        CustomerResponse response = customerService.saveCustomer(customerRequest);
        URI location =
                ServletUriComponentsBuilder.fromCurrentRequest()
                        .path("/api/customers/{id}")
                        .buildAndExpand(response.customerId())
                        .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<CustomerResponse> updateCustomer(
            @PathVariable Long id, @RequestBody @Valid CustomerRequest customerRequest) {
        return ResponseEntity.ok(customerService.updateCustomer(id, customerRequest));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<CustomerResponse> deleteCustomer(@PathVariable Long id) {
        return customerService
                .findCustomerById(id)
                .map(
                        customer -> {
                            customerService.deleteCustomerById(id);
                            return ResponseEntity.ok(customer);
                        })
                .orElseThrow(() -> new CustomerNotFoundException(id));
    }
}
