/***
 * <p>
 * Licensed under MIT License Copyright (c) 2026 Raja Kolli.
 * </p>
 ***/

package com.example.common.nativefixture.reactive;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(
        classes = ReactiveFixtureApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "spring.main.web-application-type=reactive")
@ExtendWith(OutputCaptureExtension.class)
class ReactiveFixtureTest {

    @Autowired private ApplicationContext applicationContext;

    @Autowired private ReactiveFixtureController controller;

    @LocalServerPort private int port;

    @Test
    void verifiesReactiveFixtureEndToEnd(CapturedOutput output) throws Exception {
        assertThat(applicationContext).isNotNull();
        assertThat(AopUtils.isAopProxy(controller)).isTrue();

        WebTestClient client =
                WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();

        byte[] responseBody =
                client.get()
                        .uri("/api/reactive-fixtures")
                        .exchange()
                        .expectStatus()
                        .isOk()
                        .expectBody()
                        .returnResult()
                        .getResponseBody();

        assertThat(responseBody).isNotNull();

        JsonMapper mapper = JsonMapper.builder().build();
        JsonNode root = mapper.readTree(responseBody);

        assertThat(root.has("data")).isTrue();
        assertThat(root.get("data").isArray()).isTrue();
        assertThat(root.get("data").get(0).get("id").asString()).isEqualTo("1");
        assertThat(root.get("data").get(0).get("name").asString()).isEqualTo("item-1");
        assertThat(root.has("totalElements")).isTrue();
        assertThat(root.has("pageNumber")).isTrue();
        assertThat(root.has("totalPages")).isTrue();
        assertThat(root.has("isFirst")).isTrue();
        assertThat(root.has("isLast")).isTrue();
        assertThat(root.has("hasNext")).isTrue();
        assertThat(root.has("hasPrevious")).isTrue();

        assertThat(output.getOut())
                .contains("getFixtures() start execution")
                .contains("getFixtures() finished execution");
    }
}
