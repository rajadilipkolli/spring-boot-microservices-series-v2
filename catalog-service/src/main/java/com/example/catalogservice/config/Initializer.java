/***
<p>
    Licensed under MIT License Copyright (c) 2021-2023 Raja Kolli.
</p>
***/

package com.example.catalogservice.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class Initializer implements CommandLineRunner {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private final ApplicationProperties properties;

    public Initializer(ApplicationProperties properties) {
        this.properties = properties;
    }

    @Override
    public void run(String... args) {
        log.info("Running Initializer.....{}", properties.inventoryServiceUrl());
    }
}
