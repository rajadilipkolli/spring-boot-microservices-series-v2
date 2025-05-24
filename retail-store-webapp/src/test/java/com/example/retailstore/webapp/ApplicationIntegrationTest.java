package com.example.retailstore.webapp;

import com.example.retailstore.webapp.common.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

public class ApplicationIntegrationTest extends AbstractIntegrationTest {

    @Test
    void context() {
        mockMvcTester
                .get()
                .uri("/actuator/health")
                .assertThat()
                .hasContentType("application/vnd.spring-boot.actuator.v3+json")
                .hasHeader("Cache-Control", "no-cache, no-store, max-age=0, must-revalidate")
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("UP");
    }
}
