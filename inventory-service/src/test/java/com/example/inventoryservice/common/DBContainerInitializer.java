/* Licensed under Apache-2.0 2021-2022 */
package com.example.inventoryservice.common;

import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;

public class DBContainerInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    public static final PostgreSQLContainer<?> POSTGRE_SQL_CONTAINER =
            new PostgreSQLContainer<>("postgres:15-alpine")
                    .withDatabaseName("integration-tests-db")
                    .withUsername("username")
                    .withPassword("password");

    static {
        POSTGRE_SQL_CONTAINER.start();
    }

    public void initialize(ConfigurableApplicationContext configurableApplicationContext) {
        TestPropertyValues.of(
                        "spring.datasource.url=" + POSTGRE_SQL_CONTAINER.getJdbcUrl(),
                        "spring.datasource.username=" + POSTGRE_SQL_CONTAINER.getUsername(),
                        "spring.datasource.password=" + POSTGRE_SQL_CONTAINER.getPassword())
                .applyTo(configurableApplicationContext.getEnvironment());
    }
}
