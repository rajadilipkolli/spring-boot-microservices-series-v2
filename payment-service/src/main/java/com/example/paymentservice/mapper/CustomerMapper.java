/*** Licensed under MIT License Copyright (c) 2023-2026 Raja Kolli. ***/
package com.example.paymentservice.mapper;

import com.example.paymentservice.entities.Customer;
import com.example.paymentservice.model.request.CustomerRequest;
import com.example.paymentservice.model.response.CustomerResponse;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CustomerMapper {

    /**
     * Creates a customer with the request's contact details, available balance, and separate
     * address fields, with a zero reserved balance.
     *
     * @param customerRequest customer details to copy
     * @return a new customer entity
     */
    public Customer toEntity(CustomerRequest customerRequest) {
        Customer customer = new Customer();
        customer.setName(customerRequest.name());
        customer.setEmail(customerRequest.email());
        customer.setAddressLine1(customerRequest.addressLine1());
        customer.setAddressLine2(customerRequest.addressLine2());
        customer.setCity(customerRequest.city());
        customer.setState(customerRequest.state());
        customer.setZipCode(customerRequest.zipCode());
        customer.setCountry(customerRequest.country());
        customer.setPhone(customerRequest.phone());
        customer.setAmountAvailable(customerRequest.amountAvailable());
        customer.setAmountReserved(BigDecimal.ZERO);
        return customer;
    }

    /**
     * Maps customer details, separate address fields, and the available balance to a response.
     *
     * @param customer the entity to read
     * @return the customer response
     */
    public CustomerResponse toResponse(Customer customer) {
        return new CustomerResponse(
                customer.getId(),
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
    }

    /**
     * Updates a customer's contact details, available balance, and address fields.
     *
     * @param customer the entity to update
     * @param customerRequest replacement customer details
     */
    public void mapCustomerWithRequest(Customer customer, CustomerRequest customerRequest) {
        customer.setAmountAvailable(customerRequest.amountAvailable());
        customer.setName(customerRequest.name());
        customer.setAddressLine1(customerRequest.addressLine1());
        customer.setAddressLine2(customerRequest.addressLine2());
        customer.setCity(customerRequest.city());
        customer.setState(customerRequest.state());
        customer.setZipCode(customerRequest.zipCode());
        customer.setCountry(customerRequest.country());
        customer.setEmail(customerRequest.email());
        customer.setPhone(customerRequest.phone());
    }

    public List<CustomerResponse> toListResponse(List<Customer> customerList) {
        return customerList.stream().map(this::toResponse).toList();
    }
}
