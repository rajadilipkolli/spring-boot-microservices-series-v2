/*** Licensed under MIT License Copyright (c) 2023-2024 Raja Kolli. ***/
package com.example.paymentservice;

import com.example.paymentservice.common.NonSQLContainerConfig;
import com.example.paymentservice.common.SQLContainerConfig;
import org.springframework.boot.SpringApplication;

public class TestPaymentApplication {

    public static void main(String[] args) {
        System.setProperty("spring.profiles.active", "local");
        SpringApplication.from(PaymentApplication::main)
                .with(SQLContainerConfig.class, NonSQLContainerConfig.class)
                .run(args);
    }
}
