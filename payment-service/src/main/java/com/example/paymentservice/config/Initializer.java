/* Licensed under Apache-2.0 2021-2023 */
package com.example.paymentservice.config;

import com.example.paymentservice.entities.Customer;
import com.example.paymentservice.repositories.CustomerRepository;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.datafaker.Faker;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class Initializer implements CommandLineRunner {

    private final CustomerRepository customerRepository;

    @Override
    public void run(String... args) {
        log.info("Running Initializer.....");
        SecureRandom secureRandom = new SecureRandom();
        Faker faker = new Faker();
        List<Customer> customerList = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            int count = secureRandom.nextInt(1000);
            Customer customer =
                    new Customer(
                            null,
                            faker.name().fullName(),
                            faker.name().lastName() + "@gmail.com",
                            faker.address().fullAddress(),
                            count,
                            0);
            customerList.add(customer);
        }
        // Using BatchMode to save Entities
        this.customerRepository.saveAll(customerList);
    }
}
