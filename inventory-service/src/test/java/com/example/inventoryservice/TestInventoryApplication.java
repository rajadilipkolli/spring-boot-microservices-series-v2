/***
<p>
    Licensed under MIT License Copyright (c) 2023-2024 Raja Kolli.
</p>
***/

package com.example.inventoryservice;

import com.example.inventoryservice.common.NonSQLContainersConfig;
import com.example.inventoryservice.common.SQLContainersConfig;
import com.example.inventoryservice.utils.AppConstants;
import org.springframework.boot.SpringApplication;

public class TestInventoryApplication {

    public static void main(String[] args) {
        SpringApplication.from(InventoryServiceApplication::main)
                .with(NonSQLContainersConfig.class, SQLContainersConfig.class)
                .withAdditionalProfiles(AppConstants.PROFILE_TEST)
                .run(args);
    }
}
