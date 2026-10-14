/*** Licensed under MIT License Copyright (c) 2023-2026 Raja Kolli. ***/
package com.example.paymentservice.mapper;

import com.example.paymentservice.jooq.tables.records.CustomersRecord;
import com.example.paymentservice.model.request.CustomerRequest;
import com.example.paymentservice.model.response.CustomerResponse;
import java.util.List;
import org.jooq.OrderField;
import org.jooq.TableField;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class CustomerMapper {

    /**
     * Updates a customer's contact details, available balance, and address fields.
     *
     * @param customer the entity to update
     * @param customerRequest replacement customer details
     */
    public void mapCustomerWithRequest(CustomersRecord customer, CustomerRequest customerRequest) {
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

    public List<CustomerResponse> toListResponse(List<CustomersRecord> customerList) {
        return customerList.stream().map(CustomerResponse::toResponse).toList();
    }

    public List<OrderField<?>> toOrderFields(String sortBy, String sortDir) {
        boolean ascending = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name());
        TableField<CustomersRecord, ?> orderField;
        switch (sortBy) {
            case "name" ->
                    orderField = com.example.paymentservice.jooq.tables.Customers.CUSTOMERS.NAME;
            case "email" ->
                    orderField = com.example.paymentservice.jooq.tables.Customers.CUSTOMERS.EMAIL;
            case "amountAvailable" ->
                    orderField =
                            com.example.paymentservice.jooq.tables.Customers.CUSTOMERS
                                    .AMOUNT_AVAILABLE;
            default -> orderField = com.example.paymentservice.jooq.tables.Customers.CUSTOMERS.ID;
        }
        return List.of(ascending ? orderField.asc() : orderField.desc());
    }

    public void mapCustomerWithRecord(
            CustomersRecord existingRecord, CustomersRecord requestCustomer) {
        existingRecord.setAmountAvailable(requestCustomer.getAmountAvailable());
        existingRecord.setAmountReserved(requestCustomer.getAmountReserved());
        existingRecord.setName(requestCustomer.getName());
        existingRecord.setAddressLine1(requestCustomer.getAddressLine1());
        existingRecord.setAddressLine2(requestCustomer.getAddressLine2());
        existingRecord.setCity(requestCustomer.getCity());
        existingRecord.setState(requestCustomer.getState());
        existingRecord.setZipCode(requestCustomer.getZipCode());
        existingRecord.setCountry(requestCustomer.getCountry());
        existingRecord.setEmail(requestCustomer.getEmail());
        existingRecord.setPhone(requestCustomer.getPhone());
    }
}
