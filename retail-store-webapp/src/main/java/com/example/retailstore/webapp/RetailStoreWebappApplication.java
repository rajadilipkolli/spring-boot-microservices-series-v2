package com.example.retailstore.webapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class RetailStoreWebappApplication {

    public static void main(String[] args) {
        SpringApplication.run(RetailStoreWebappApplication.class, args);
    }
}
