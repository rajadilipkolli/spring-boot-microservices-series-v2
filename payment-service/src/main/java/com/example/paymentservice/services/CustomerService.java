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
import org.postgresql.util.PSQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
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

    /**
     * Fetches a sorted customer page with counts and navigation metadata.
     *
     * @param findCustomersQuery the one-based page number, page size, and sort options
     * @return the requested page of customer responses
     * @throws IllegalArgumentException if the page number or page size is less than one
     */
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

    /**
     * Finds customer details by identifier.
     *
     * @param id the customer identifier
     * @return the matching response, or empty if absent
     */
    public Optional<CustomerResponse> findCustomerById(Long id) {
        return customerRepository.findById(id).map(CustomerResponse::toResponse);
    }

    /**
     * Finds customer details by an exact name match.
     *
     * @param name the customer name
     * @return the matching response, or empty if absent
     */
    public Optional<CustomerResponse> findCustomerByName(String name) {
        return customerRepository.findByName(name).map(CustomerResponse::toResponse);
    }

    /**
     * Finds customer details after lowercasing the email using the root locale.
     *
     * @param email the email to look up, or null for an empty result
     * @return the matching response, or empty if absent
     */
    public Optional<CustomerResponse> findCustomerByEmail(String email) {
        String normalizedEmail = email == null ? null : email.toLowerCase(Locale.ROOT);
        return customerRepository.findByEmail(normalizedEmail).map(CustomerResponse::toResponse);
    }

    /**
     * Creates a customer after checking the normalized email for duplicates.
     *
     * @param customerRequest the customer details and opening available balance
     * @return the persisted customer response
     * @throws CustomerExistsException if the email is already present or a concurrent insert
     *     violates the customer email constraint
     * @throws DataIntegrityViolationException if another database integrity constraint fails
     */
    @Transactional
    public CustomerResponse saveCustomer(CustomerRequest customerRequest) {
        String normalizedEmail = customerRequest.email().toLowerCase(Locale.ROOT);

        if (customerRepository.existsByEmail(normalizedEmail)) {
            log.warn("Customer with email {} already exists.", normalizedEmail);
            throw new CustomerExistsException(
                    "Customer with email " + normalizedEmail + " already exists.");
        }

        CustomersRecord savedCustomer;
        try {
            savedCustomer = customerRepository.saveCustomer(customerRequest.toCustomersRecord());
        } catch (DataIntegrityViolationException e) {
            if (isEmailUniqueConstraintViolation(e)) {
                throw new CustomerExistsException(
                        "Customer with email " + normalizedEmail + " already exists.");
            }
            throw e;
        }
        return CustomerResponse.toResponse(savedCustomer);
    }

    /**
     * Checks nested PostgreSQL causes for a unique violation of the customer email constraint.
     *
     * @param exception the persistence failure to inspect
     * @return whether a cause identifies SQL state 23505 and the exact customer email constraint
     */
    private boolean isEmailUniqueConstraintViolation(DataIntegrityViolationException exception) {
        for (Throwable cause = exception.getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof PSQLException sqlException
                    && "23505".equals(sqlException.getSQLState())
                    && sqlException.getServerErrorMessage() != null
                    && "uc_customers_name_email_key"
                            .equals(sqlException.getServerErrorMessage().getConstraint())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Replaces customer details and available funds while preserving reserved funds.
     *
     * @param id the identifier of the customer to update
     * @param customerRequest the replacement contact details and available balance
     * @return the persisted customer response
     * @throws CustomerNotFoundException if no customer has the supplied ID
     * @throws org.springframework.dao.OptimisticLockingFailureException if the customer changes
     *     during the update
     */
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

    /** Deletes the customer by ID and returns the number of deleted rows. */
    @Transactional
    public int deleteCustomerById(Long id) {
        return customerRepository.deleteById(id);
    }
}
