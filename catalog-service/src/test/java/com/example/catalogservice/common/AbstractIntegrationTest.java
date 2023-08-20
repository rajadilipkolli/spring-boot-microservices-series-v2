/*** Licensed under Apache-2.0 2021-2023 ***/
package com.example.catalogservice.common;

import static com.example.catalogservice.utils.AppConstants.PROFILE_TEST;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import com.example.catalogservice.TestCatalogServiceApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

@ActiveProfiles({PROFILE_TEST})
@SpringBootTest(
        webEnvironment = RANDOM_PORT,
        properties = {"spring.cloud.config.enabled=false"},
        classes = TestCatalogServiceApplication.class)
@AutoConfigureWebTestClient
public abstract class AbstractIntegrationTest {

    @Autowired protected WebTestClient webTestClient;
}
