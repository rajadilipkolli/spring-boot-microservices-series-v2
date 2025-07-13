package com.example.retailstore.webapp.common;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.wiremock.spring.ConfigureWireMock;
import org.wiremock.spring.EnableWireMock;
import org.wiremock.spring.InjectWireMock;

@SpringBootTest(
        webEnvironment = RANDOM_PORT,
        classes = {ContainerConfig.class})
@AutoConfigureMockMvc
@EnableWireMock({@ConfigureWireMock(name = "gateway-service", baseUrlProperties = "retailstore.api-gateway-url")})
public abstract class AbstractIntegrationTest {

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected MockMvcTester mockMvcTester;

    @Autowired
    protected KeycloakContainer keycloakContainer;

    @InjectWireMock("gateway-service")
    protected WireMockServer gatewayServiceMock;
}
