/* Licensed under Apache-2.0 2021-2022 */
package com.example.inventoryservice;

import static com.example.inventoryservice.common.DBContainerInitializer.POSTGRE_SQL_CONTAINER;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.inventoryservice.common.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

class InventoryServiceApplicationIntegrationTest extends AbstractIntegrationTest {

    @Test
    void contextLoads() {
        assertThat(POSTGRE_SQL_CONTAINER.isRunning()).isTrue();
    }
}
