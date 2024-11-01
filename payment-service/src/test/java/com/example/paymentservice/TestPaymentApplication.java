/*** Licensed under MIT License Copyright (c) 2023-2024 Raja Kolli. ***/
package com.example.paymentservice;

import com.example.paymentservice.common.NonSQLContainerConfig;
import com.example.paymentservice.common.SQLContainerConfig;
import com.example.paymentservice.utils.AppConstants;
import org.springframework.boot.SpringApplication;

public class TestPaymentApplication {

    public static void main(String[] args) {
        SpringApplication.from(PaymentApplication::main)
                .with(SQLContainerConfig.class, NonSQLContainerConfig.class)
                .withAdditionalProfiles(AppConstants.PROFILE_LOCAL)
                .run(args);
    }
}
