/* Licensed under Apache-2.0 2023 */
package com.example.paymentservice;

import com.example.paymentservice.config.MyContainersConfiguration;
import org.springframework.boot.SpringApplication;

public class TestPaymentApplication {

    public static void main(String[] args) {
        SpringApplication.from(PaymentApplication::main)
                .with(MyContainersConfiguration.class)
                .run(args);
    }
}
