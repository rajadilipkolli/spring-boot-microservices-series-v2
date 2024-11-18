/***
<p>
    Licensed under MIT License Copyright (c) 2023 Raja Kolli.
</p>
***/

package com.example.inventoryservice.repositories;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.inventoryservice.common.SQLContainersConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest(
        properties = {
            "spring.jpa.hibernate.ddl-auto=validate",
            "spring.cloud.config.enabled=false"
        })
@Import(SQLContainersConfig.class)
@AutoConfigureTestDatabase
class InventoryRepositoryTest {

    @Autowired private DataSource datasource;

    @Test
    void dataSource() {
        assertThat(datasource).isNotNull().isInstanceOf(HikariDataSource.class);
    }
}
