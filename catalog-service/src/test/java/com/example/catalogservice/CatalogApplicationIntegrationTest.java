package com.example.catalogservice;

import static com.example.catalogservice.common.DBContainerInitializer.POSTGRE_SQL_CONTAINER;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.catalogservice.common.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

class CatalogApplicationIntegrationTest extends AbstractIntegrationTest {

    @Test
    void contextLoads() {
        assertThat(POSTGRE_SQL_CONTAINER.isRunning()).isTrue();
    }
}
