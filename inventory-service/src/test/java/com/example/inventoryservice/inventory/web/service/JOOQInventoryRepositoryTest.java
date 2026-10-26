/***
<p>
    Licensed under MIT License Copyright (c) 2023-2026 Raja Kolli.
</p>
***/

package com.example.inventoryservice.inventory.web.service;

import static com.example.inventoryservice.inventory.jooq.tables.Inventory.INVENTORY;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.inventoryservice.inventory.jooq.tables.records.InventoryRecord;
import com.example.inventoryservice.inventory.shared.SQLContainersConfig;
import java.util.List;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jooq.test.autoconfigure.JooqTest;
import org.springframework.context.annotation.Import;

@JooqTest(properties = {"spring.cloud.config.enabled=false"})
@Import({SQLContainersConfig.class})
@AutoConfigureTestDatabase
class JOOQInventoryRepositoryTest {

    @Autowired private DSLContext dslContext;

    @BeforeEach
    void setUpData() {
        dslContext.deleteFrom(INVENTORY).execute();
    }

    @Test
    void findByProductCodeInAndQuantityAvailable() {
        List<InventoryRecord> inventoryList =
                List.of(
                        new InventoryRecord()
                                .setId(io.hypersistence.tsid.TSID.fast().toLong())
                                .setProductCode("product1")
                                .setQuantity(10)
                                .setReservedItems(0),
                        new InventoryRecord()
                                .setId(io.hypersistence.tsid.TSID.fast().toLong())
                                .setProductCode("product2")
                                .setQuantity(0)
                                .setReservedItems(0));
        inventoryList.forEach(inventory -> dslContext.newRecord(INVENTORY, inventory).insert());

        List<InventoryRecord> findAvailableInventory =
                dslContext
                        .selectFrom(INVENTORY)
                        .where(
                                INVENTORY
                                        .PRODUCT_CODE
                                        .in(List.of("product1", "product2"))
                                        .and(INVENTORY.QUANTITY.gt(0)))
                        .fetch();

        assertThat(findAvailableInventory).isNotEmpty().hasSize(1);

        boolean exists = dslContext.fetchExists(INVENTORY, INVENTORY.PRODUCT_CODE.eq("product2"));
        assertThat(exists).isTrue();

        exists = dslContext.fetchExists(INVENTORY, INVENTORY.PRODUCT_CODE.eq("product200"));
        assertThat(exists).isFalse();
    }
}
