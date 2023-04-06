/* Licensed under Apache-2.0 2022 */
package com.example.paymentservice.services;

import com.example.paymentservice.entities.Customer;
import com.example.paymentservice.model.response.PagedResult;
import com.example.paymentservice.repositories.CustomerRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
@Service
@Transactional
public class CustomerService {

    private final CustomerRepository customerRepository;

    @Transactional(readOnly = true)
    public PagedResult<Customer> findAllCustomers(
            int pageNo, int pageSize, String sortBy, String sortDir) {

        log.info(
                "Fetching findAllCustomers for pageNo {} with pageSize {}, sorting BY {} {}",
                pageNo,
                pageSize,
                sortBy,
                sortDir);

        Sort sort =
                sortDir.equalsIgnoreCase(Sort.Direction.ASC.name())
                        ? Sort.by(sortBy).ascending()
                        : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(pageNo, pageSize, sort);
        Page<Customer> page = customerRepository.findAll(pageable);
        return new PagedResult<>(page);
    }

    @Transactional(readOnly = true)
    public Optional<Customer> findCustomerById(Long id) {
        return customerRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<Customer> findCustomerByName(String name) {
        return customerRepository.findByName(name);
    }

    public Customer saveCustomer(Customer customer) {
        return customerRepository.save(customer);
    }

    public void deleteCustomerById(Long id) {
        customerRepository.deleteById(id);
    }
}
