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
     * Updates a customersRecord's contact details, available balance, and address fields.
     *
     * @param customersRecord the entity to update
     * @param customerRequest replacement customersRecord details
     */
    public void mapCustomerWithRequest(
            CustomersRecord customersRecord, CustomerRequest customerRequest) {
        customersRecord
                .setAmountAvailable(customerRequest.amountAvailable())
                .setName(customerRequest.name())
                .setAddressLine1(customerRequest.addressLine1())
                .setAddressLine2(customerRequest.addressLine2())
                .setCity(customerRequest.city())
                .setState(customerRequest.state())
                .setZipCode(customerRequest.zipCode())
                .setCountry(customerRequest.country())
                .setEmail(customerRequest.email())
                .setPhone(customerRequest.phone());
    }

    /**
     * Converts customer records to response DTOs in encounter order.
     *
     * @param customerList the records to convert
     * @return an unmodifiable list of customer responses
     */
    public List<CustomerResponse> toListResponse(List<CustomersRecord> customerList) {
        return customerList.stream().map(CustomerResponse::toResponse).toList();
    }

    /**
     * Builds a customer sort expression, defaulting unknown properties to the ID field.
     *
     * @param sortBy the name, email, amountAvailable, or fallback ID property
     * @param sortDir ascending when equal to "ASC", ignoring case; descending otherwise
     * @return a single sort expression for the selected field
     */
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
}
