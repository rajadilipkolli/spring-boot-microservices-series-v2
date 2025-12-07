/*** Licensed under MIT License Copyright (c) 2021-2024 Raja Kolli. ***/
package com.example.paymentservice.config;

import com.example.paymentservice.entities.Customer;
import com.example.paymentservice.repositories.CustomerRepository;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import net.datafaker.Faker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
class Initializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(Initializer.class);

    private final CustomerRepository customerRepository;

    public Initializer(final CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Override
    public void run(String... args) {
        log.info("Running Initializer.....");
        SecureRandom secureRandom = new SecureRandom();
        Faker faker = new Faker();
        List<Customer> customerList = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            int count = secureRandom.nextInt(1000);
            Customer customer =
                    new Customer()
                            .setName(faker.name().fullName())
                            .setEmail(faker.name().lastName() + "@gmail.com")
                            .setAddress(faker.address().fullAddress())
                            .setPhone(faker.phoneNumber().phoneNumber())
                            .setAmountAvailable(count)
                            .setAmountReserved(0);
            customerList.add(customer);
        }
        Customer retailCustomer = new Customer()
                .setName("retail")
                .setEmail("retail@gmail.com")
                .setAddress(faker.address().fullAddress())
                .setPhone(faker.phoneNumber().phoneNumber())
                .setAmountAvailable(secureRandom.nextInt(1000))
                .setAmountReserved(0);
        customerList.add(retailCustomer);
        // Using BatchMode to save Entities
        this.customerRepository.saveAll(customerList);
    }
}
