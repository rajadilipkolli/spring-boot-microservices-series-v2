/*** Licensed under MIT License Copyright (c) 2022-2023 Raja Kolli. ***/
package com.example.paymentservice.services;

import com.example.paymentservice.config.logging.Loggable;
import com.example.paymentservice.entities.Customer;
import com.example.paymentservice.exception.CustomerNotFoundException;
import com.example.paymentservice.mapper.CustomerMapper;
import com.example.paymentservice.model.query.FindCustomersQuery;
import com.example.paymentservice.model.request.CustomerRequest;
import com.example.paymentservice.model.response.CustomerResponse;
import com.example.paymentservice.model.response.PagedResult;
import com.example.paymentservice.repositories.CustomerRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Service
@Transactional(readOnly = true)
@Loggable
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final CustomerMapper customerMapper;

    public PagedResult<CustomerResponse> findAllCustomers(FindCustomersQuery findCustomersQuery) {
        log.info(
                "Fetching findAllCustomers for pageNo {} with pageSize {}, sorting By {} {}",
                findCustomersQuery.pageNo() - 1,
                findCustomersQuery.pageSize(),
                findCustomersQuery.sortBy(),
                findCustomersQuery.sortDir());

        Pageable pageable = createPageable(findCustomersQuery);
        Page<Customer> page = customerRepository.findAll(pageable);

        List<CustomerResponse> customerResponseList =
                customerMapper.toListResponse(page.getContent());
        return new PagedResult<>(customerResponseList, page);
    }

    private Pageable createPageable(FindCustomersQuery findCustomersQuery) {
        int pageNo = Math.max(findCustomersQuery.pageNo() - 1, 0);
        Sort sort =
                Sort.by(
                        findCustomersQuery.sortDir().equalsIgnoreCase(Sort.Direction.ASC.name())
                                ? Sort.Order.asc(findCustomersQuery.sortBy())
                                : Sort.Order.desc(findCustomersQuery.sortBy()));
        return PageRequest.of(pageNo, findCustomersQuery.pageSize(), sort);
    }

    public Optional<CustomerResponse> findCustomerById(Long id) {
        return customerRepository.findById(id).map(customerMapper::toResponse);
    }

    public Optional<CustomerResponse> findCustomerByName(String name) {
        return customerRepository.findByName(name);
    }

    @Transactional
    public CustomerResponse saveCustomer(CustomerRequest customerRequest) {
        Customer customer = customerMapper.toEntity(customerRequest);
        return customerMapper.toResponse(customerRepository.save(customer));
    }

    @Transactional
    public CustomerResponse updateCustomer(Long id, CustomerRequest customerRequest) {
        Customer customer =
                customerRepository
                        .findById(id)
                        .orElseThrow(() -> new CustomerNotFoundException(id));

        // Update the customer object with data from customerRequest
        customerMapper.mapCustomerWithRequest(customer, customerRequest);

        // Save the updated customer object
        Customer updatedCustomer = customerRepository.save(customer);

        // Map the updated customer to a response object and return it
        return customerMapper.toResponse(updatedCustomer);
    }

    @Transactional
    public void deleteCustomerById(Long id) {
        customerRepository.deleteById(id);
    }
}
