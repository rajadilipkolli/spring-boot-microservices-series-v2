/***
 * <p>
 * Licensed under MIT License Copyright (c) 2026 Raja Kolli.
 * </p>
 ***/

package com.example.common.nativefixture.servlet;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ExtendWith(OutputCaptureExtension.class)
class ServletFixtureTest {

    @Autowired private ApplicationContext applicationContext;

    @Autowired private ServletFixtureController controller;

    @Autowired private TestRestTemplate restTemplate;

    @Test
    void verifiesServletFixtureEndToEnd(CapturedOutput output) throws Exception {
        assertThat(applicationContext).isNotNull();
        assertThat(AopUtils.isAopProxy(controller)).isTrue();

        ResponseEntity<String> response = restTemplate.getForEntity("/api/fixtures", String.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

        JsonMapper mapper = JsonMapper.builder().build();
        JsonNode root = mapper.readTree(response.getBody());

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
