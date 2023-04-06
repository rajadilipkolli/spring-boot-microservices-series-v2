/* Licensed under Apache-2.0 2021-2022 */
package com.example.orderservice.config;

import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class Initializer implements CommandLineRunner {

    @Override
    public void run(String... args) {
        log.info("Running Initializer.....");
    }
}
