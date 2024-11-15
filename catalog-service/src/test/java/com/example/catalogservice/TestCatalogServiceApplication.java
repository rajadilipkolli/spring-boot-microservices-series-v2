/***
<p>
    Licensed under MIT License Copyright (c) 2023-2024 Raja Kolli.
</p>
***/

package com.example.catalogservice;

import com.example.catalogservice.common.ContainersConfig;
import com.example.catalogservice.common.SQLContainerConfig;
import com.example.catalogservice.utils.AppConstants;
import org.springframework.boot.SpringApplication;

public class TestCatalogServiceApplication {

    public static void main(String[] args) {
        SpringApplication.from(CatalogServiceApplication::main)
                .with(ContainersConfig.class, SQLContainerConfig.class)
                .withAdditionalProfiles(AppConstants.PROFILE_TEST)
                .run(args);
    }
}
