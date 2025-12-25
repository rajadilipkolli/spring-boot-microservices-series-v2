package com.example.retailstore.webapp.common;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import com.github.tomakehurst.wiremock.WireMockServer;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.wiremock.spring.ConfigureWireMock;
import org.wiremock.spring.EnableWireMock;
import org.wiremock.spring.InjectWireMock;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(
        webEnvironment = RANDOM_PORT,
        classes = {ContainerConfig.class})
@AutoConfigureMockMvc
@EnableWireMock({@ConfigureWireMock(name = "gateway-service", baseUrlProperties = "retailstore.api-gateway-url")})
public abstract class AbstractIntegrationTest {

    @Autowired
    protected JsonMapper jsonMapper;

    @Autowired
    protected MockMvcTester mockMvcTester;

    @Autowired
    protected KeycloakContainer keycloakContainer;

    @InjectWireMock("gateway-service")
    protected WireMockServer gatewayServiceMock;
}
