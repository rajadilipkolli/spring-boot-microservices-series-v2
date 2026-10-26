/***
<p>
    Licensed under MIT License Copyright (c) 2023-2025 Raja Kolli.
</p>
***/

package com.example.inventoryservice.inventory.web.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.inventoryservice.inventory.shared.SQLContainersConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest(
        properties = {
            "spring.jpa.hibernate.ddl-auto=validate",
            "spring.cloud.config.enabled=false"
        })
@Import(SQLContainersConfig.class)
@AutoConfigureTestDatabase
class InventoryRepositoryTest {

    @Autowired private DataSource datasource;
    @Autowired private InventoryRepository inventoryRepository;

    @Test
    void dataSource() {
        assertThat(datasource).isNotNull().isInstanceOf(HikariDataSource.class);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void shouldDeleteByProductCodeWithoutAnExistingTransaction() {
        String productCode = "repository-delete-transaction";
        inventoryRepository.saveAndFlush(new Inventory().setProductCode(productCode).markDeleted());

        inventoryRepository.deleteByProductCode(productCode);

        assertThat(inventoryRepository.findByProductCode(productCode)).isEmpty();
    }
}
