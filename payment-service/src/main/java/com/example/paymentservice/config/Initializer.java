/*** Licensed under MIT License Copyright (c) 2021-2026 Raja Kolli. ***/
package com.example.paymentservice.config;

import com.example.paymentservice.jooq.tables.records.CustomersRecord;
import com.example.paymentservice.repositories.CustomerRepository;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import net.datafaker.Faker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

@Component
class Initializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(Initializer.class);

    private final CustomerRepository customerRepository;

    public Initializer(final CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    /**
     * Seeds random sample customers and creates the retail and Raja customers when absent. A
     * duplicate-key failure for Raja is ignored only if a second lookup confirms concurrent
     * creation. Exceptions while creating the retail customer are suppressed; batch insertion,
     * lookup, and other Raja creation failures propagate.
     *
     * @param args unused command-line arguments
     */
    @Override
    public void run(String... args) {
        log.info("Running Initializer.....");
        SecureRandom secureRandom = new SecureRandom();
        Faker faker = new Faker();
        List<CustomersRecord> customersRecordList = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            int randomNumber = secureRandom.nextInt(10_000);
            CustomersRecord customersRecord =
                    new CustomersRecord()
                            .setName(faker.name().fullName())
                            .setEmail(faker.name().lastName() + i + "@gmail.com")
                            .setAmountAvailable(BigDecimal.valueOf(randomNumber))
                            .setAmountReserved(BigDecimal.ZERO);
            createCustomerRecordAddress(faker, customersRecord);
            customersRecordList.add(customersRecord);
        }

        // Using BatchMode to save Entities
        this.customerRepository.saveAll(customersRecordList);

        // Save retail customer separately with conflict handling
        if (!this.customerRepository.existsByEmail("retailstore@gmail.com")) {
            try {
                CustomersRecord retailCustomer =
                        new CustomersRecord().setName("retail").setEmail("retailstore@gmail.com");
                setCustomerRecord(secureRandom, faker, retailCustomer);
            } catch (Exception e) {
                // Handle race condition - another instance may have created the retail customer
                log.debug(
                        "Retail customer already exists (concurrent creation): {}", e.getMessage());
            }
        }

        // Save raja customer separately with conflict handling
        if (!this.customerRepository.existsByEmail("rajakolli@gmail.com")) {
            try {
                CustomersRecord rajaCustomer =
                        new CustomersRecord().setName("raja").setEmail("rajakolli@gmail.com");
                setCustomerRecord(secureRandom, faker, rajaCustomer);
            } catch (DuplicateKeyException e) {
                if (!this.customerRepository.existsByEmail("rajakolli@gmail.com")) {
                    throw e;
                }
                // Another instance created the customer after the initial lookup.
                log.debug("Raja customer already exists (concurrent creation): {}", e.getMessage());
            }
        }
    }

    /**
     * Populates the supplied record with generated address and phone details.
     *
     * @param faker the generator for sample contact details
     * @param customersRecord the record to update
     */
    private void createCustomerRecordAddress(Faker faker, CustomersRecord customersRecord) {
        customersRecord
                .setAddressLine1(faker.address().streetAddress())
                .setAddressLine2(faker.address().buildingNumber())
                .setCity(faker.address().city())
                .setState(faker.address().stateAbbr())
                .setZipCode(faker.address().zipCode())
                .setCountry(faker.address().country())
                .setPhone(faker.phoneNumber().phoneNumber());
    }

    /**
     * Adds sample contact details and balances to a named customer, then persists it.
     *
     * @param secureRandom the generator for an available balance below 100,000
     * @param faker the generator for sample contact details
     * @param customersRecord the record with its name and email already set
     */
    private void setCustomerRecord(
            SecureRandom secureRandom, Faker faker, CustomersRecord customersRecord) {
        createCustomerRecordAddress(faker, customersRecord);
        customersRecord
                .setAmountAvailable(BigDecimal.valueOf(secureRandom.nextInt(100_000)))
                .setAmountReserved(BigDecimal.ZERO);
        this.customerRepository.saveCustomer(customersRecord);
    }
}
