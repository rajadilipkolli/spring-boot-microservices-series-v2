/***
<p>
    Licensed under MIT License Copyright (c) 2023-2024 Raja Kolli.
</p>
***/

package com.example.inventoryservice;

import com.example.inventoryservice.config.NonSQLContainersConfig;
import com.example.inventoryservice.config.SQLContainersConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.context.annotation.Import;

@Import(SQLContainersConfig.class)
public class TestInventoryApplication {

    public static void main(String[] args) {
        System.setProperty("spring.profiles.active", "test");
        SpringApplication.from(InventoryServiceApplication::main)
                .with(NonSQLContainersConfig.class)
                .run(args);
    }
}
