/***
<p>
    Licensed under MIT License Copyright (c) 2023 Raja Kolli.
</p>
***/

package com.example.inventoryservice.repositories;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.inventoryservice.common.ContainersConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest(
        properties = {
            "spring.jpa.hibernate.ddl-auto=validate",
            "spring.test.database.replace=none"
        })
@Import(ContainersConfig.class)
class InventoryRepositoryTest {

    @Autowired private DataSource datasource;

    @Test
    void testDataSource() {
        assertThat(datasource).isNotNull().isInstanceOf(HikariDataSource.class);
    }
}
