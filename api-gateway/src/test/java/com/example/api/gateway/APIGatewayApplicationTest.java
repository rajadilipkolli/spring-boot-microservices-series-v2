/* Licensed under Apache-2.0 2021-2022 */
package com.example.api.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.api.gateway.web.AuthenticationRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.AutoConfigureWebClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@AutoConfigureWebClient
class APIGatewayApplicationTest {

    @Container
    static final MongoDBContainer mongoDBContainer =
            new MongoDBContainer(DockerImageName.parse("mongo:5.0.12"));

    static {
        Startables.deepStart(mongoDBContainer).join();
    }

    @DynamicPropertySource
    static void registerApplicationProperties(DynamicPropertyRegistry propertyRegistry) {
        propertyRegistry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
    }

    @Autowired private WebTestClient webTestClient;

    @Test
    void contextLoads() {
        assertThat(mongoDBContainer.isRunning()).isTrue();
    }

    @Test
    void testLogin() {
        AuthenticationRequest body = new AuthenticationRequest();
        body.setUsername("user");
        body.setPassword("password");

        this.webTestClient
                .post()
                .body(BodyInserters.fromValue(body))
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }
}
