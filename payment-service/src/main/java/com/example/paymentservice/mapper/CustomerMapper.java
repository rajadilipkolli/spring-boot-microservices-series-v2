/*** Licensed under MIT License Copyright (c) 2023-2026 Raja Kolli. ***/
package com.example.paymentservice.mapper;

import com.example.paymentservice.entities.Customer;
import com.example.paymentservice.model.request.CustomerRequest;
import com.example.paymentservice.model.response.CustomerResponse;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CustomerMapper {

    public Customer toEntity(CustomerRequest customerRequest) {
        Customer customer = new Customer();
        customer.setName(customerRequest.name());
        customer.setEmail(customerRequest.email());
        customer.setAddress(getCombinedAddress(customerRequest));
        customer.setPhone(customerRequest.phone());
        customer.setAmountAvailable(customerRequest.amountAvailable());
        return customer;
    }

    public CustomerResponse toResponse(Customer customer) {
        return new CustomerResponse(
                customer.getId(),
                customer.getName(),
                customer.getEmail(),
                customer.getPhone(),
                customer.getAddress(),
                customer.getAmountAvailable());
    }

    public void mapCustomerWithRequest(Customer customer, CustomerRequest customerRequest) {
        customer.setAmountAvailable(customerRequest.amountAvailable());
        customer.setName(customerRequest.name());
        customer.setAddress(getCombinedAddress(customerRequest));
        customer.setEmail(customerRequest.email());
        customer.setPhone(customerRequest.phone());
    }

    private String getCombinedAddress(CustomerRequest request) {
        return String.join(
                ",",
                request.addressLine1() == null ? "" : request.addressLine1(),
                request.addressLine2() == null ? "" : request.addressLine2(),
                request.city() == null ? "" : request.city(),
                request.state() == null ? "" : request.state(),
                request.zipCode() == null ? "" : request.zipCode(),
                request.country() == null ? "" : request.country());
    }

    public List<CustomerResponse> toListResponse(List<Customer> customerList) {
        return customerList.stream().map(this::toResponse).toList();
    }
}
