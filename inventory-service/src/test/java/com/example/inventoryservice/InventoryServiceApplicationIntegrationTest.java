/***
<p>
    Licensed under MIT License Copyright (c) 2021-2023 Raja Kolli.
</p>
***/

package com.example.inventoryservice;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.inventoryservice.common.AbstractIntegrationTest;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class InventoryServiceApplicationIntegrationTest extends AbstractIntegrationTest {

    @Autowired private DataSource dataSource;

    @Test
    void contextLoads() {
        assertThat(dataSource).isNotNull();
    }
}
