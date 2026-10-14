/*** Licensed under MIT License Copyright (c) 2022-2026 Raja Kolli. ***/
package com.example.paymentservice.services;

import com.example.paymentservice.config.logging.Loggable;
import com.example.paymentservice.exception.CustomerExistsException;
import com.example.paymentservice.exception.CustomerNotFoundException;
import com.example.paymentservice.jooq.tables.records.CustomersRecord;
import com.example.paymentservice.mapper.CustomerMapper;
import com.example.paymentservice.model.query.FindCustomersQuery;
import com.example.paymentservice.model.request.CustomerRequest;
import com.example.paymentservice.model.response.CustomerResponse;
import com.example.paymentservice.model.response.PagedResult;
import com.example.paymentservice.repositories.CustomerRepository;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.jooq.OrderField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
@Loggable
public class CustomerService {

    private static final Logger log = LoggerFactory.getLogger(CustomerService.class);

    private final CustomerRepository customerRepository;
    private final CustomerMapper customerMapper;

    public CustomerService(CustomerRepository customerRepository, CustomerMapper customerMapper) {
        this.customerRepository = customerRepository;
        this.customerMapper = customerMapper;
    }

    public PagedResult<CustomerResponse> findAllCustomers(FindCustomersQuery findCustomersQuery) {

        int pageNo = findCustomersQuery.pageNo();
        int pageSize = findCustomersQuery.pageSize();

        if (pageNo < 1) {
            throw new IllegalArgumentException("Page number must be at least 1");
        }

        if (pageSize < 1) {
            throw new IllegalArgumentException("Page size must be at least 1");
        }

        log.info(
                "Fetching customers for pageNo {}, pageSize {}, sortBy {}, sortDir {}",
                pageNo,
                pageSize,
                findCustomersQuery.sortBy(),
                findCustomersQuery.sortDir());

        int offset = (pageNo - 1) * pageSize;

        List<OrderField<?>> orderFields =
                customerMapper.toOrderFields(
                        findCustomersQuery.sortBy(), findCustomersQuery.sortDir());

        long totalElements = customerRepository.count();

        List<CustomersRecord> records = customerRepository.findAll(offset, pageSize, orderFields);

        List<CustomerResponse> data = customerMapper.toListResponse(records);

        int totalPages = (int) Math.ceil((double) totalElements / pageSize);

        boolean isFirst = pageNo == 1;
        boolean isLast = pageNo >= totalPages;
        boolean hasNext = pageNo < totalPages;
        boolean hasPrevious = pageNo > 1 && totalElements > 0;

        return new PagedResult<>(
                data, totalElements, pageNo, totalPages, isFirst, isLast, hasNext, hasPrevious);
    }

    public Optional<CustomerResponse> findCustomerById(Long id) {
        return customerRepository.findById(id).map(CustomerResponse::toResponse);
    }

    public Optional<CustomerResponse> findCustomerByName(String name) {
        return customerRepository.findByName(name).map(CustomerResponse::toResponse);
    }

    public Optional<CustomerResponse> findCustomerByEmail(String email) {
        String normalizedEmail = email == null ? null : email.toLowerCase(Locale.ROOT);
        return customerRepository.findByEmail(normalizedEmail).map(CustomerResponse::toResponse);
    }

    @Transactional
    public CustomerResponse saveCustomer(CustomerRequest customerRequest) {
        String normalizedEmail = customerRequest.email().toLowerCase(Locale.ROOT);

        if (customerRepository.existsByEmail(normalizedEmail)) {
            log.warn("Customer with email {} already exists.", normalizedEmail);
            throw new CustomerExistsException(
                    "Customer with email " + normalizedEmail + " already exists.");
        }

        CustomersRecord savedCustomer =
                customerRepository.saveCustomer(customerRequest.toCustomersRecord());
        return CustomerResponse.toResponse(savedCustomer);
    }

    @Transactional
    public CustomerResponse updateCustomer(Long id, CustomerRequest customerRequest) {
        CustomersRecord customersRecord =
                customerRepository
                        .findById(id)
                        .orElseThrow(() -> new CustomerNotFoundException(id));

        // Update the customersRecord object with data from customerRequest
        customerMapper.mapCustomerWithRequest(customersRecord, customerRequest);

        // Save the updated customersRecord object using customerRepository with optimistic locking
        CustomersRecord updatedCustomer = customerRepository.saveCustomer(customersRecord);
        log.info("Customer with ID {} updated successfully.", id);
        return CustomerResponse.toResponse(updatedCustomer);
    }

    @Transactional
    public int deleteCustomerById(Long id) {
        return customerRepository.deleteById(id);
    }
}
