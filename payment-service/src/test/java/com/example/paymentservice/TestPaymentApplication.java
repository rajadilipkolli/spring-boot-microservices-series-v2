/* Licensed under Apache-2.0 2023 */
package com.example.paymentservice;

import com.example.paymentservice.config.MyTestContainers;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;

@TestConfiguration(proxyBeanMethods = false)
@ImportTestcontainers(MyTestContainers.class)
public class TestPaymentApplication {

    public static void main(String[] args) {
        SpringApplication.from(PaymentApplication::main)
                .with(TestPaymentApplication.class)
                .run(args);
    }
}
