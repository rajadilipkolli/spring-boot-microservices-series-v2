/*** Licensed under MIT License Copyright (c) 2026 Raja Kolli. ***/
package com.example.paymentservice.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.json.JsonMapper;

class LenientInstantDeserializerTest {

    private JsonMapper jsonMapper;

    record Sample(@JsonDeserialize(using = LenientInstantDeserializer.class) Instant createdDate) {}

    @BeforeEach
    void setUp() {
        jsonMapper = JsonMapper.builder().build();
    }

    @Test
    void deserializesOffsetFreeIsoStringAsUtc() {
        String json = "{\"createdDate\":\"2026-10-10T12:00:00\"}";
        Sample sample = jsonMapper.readValue(json, Sample.class);
        assertThat(sample.createdDate()).isEqualTo(Instant.parse("2026-10-10T12:00:00Z"));
    }

    @Test
    void deserializesZSuffixedIsoString() {
        String json = "{\"createdDate\":\"2026-10-10T12:00:00Z\"}";
        Sample sample = jsonMapper.readValue(json, Sample.class);
        assertThat(sample.createdDate()).isEqualTo(Instant.parse("2026-10-10T12:00:00Z"));
    }

    @Test
    void deserializesNumericOffsetIsoString() {
        String json = "{\"createdDate\":\"2026-10-10T17:30:00+05:30\"}";
        Sample sample = jsonMapper.readValue(json, Sample.class);
        assertThat(sample.createdDate()).isEqualTo(Instant.parse("2026-10-10T12:00:00Z"));
    }

    @Test
    void returnsNullForNullInput() {
        String json = "{\"createdDate\":null}";
        Sample sample = jsonMapper.readValue(json, Sample.class);
        assertThat(sample.createdDate()).isNull();
    }

    @Test
    void returnsNullForEmptyOrBlankInput() {
        String json = "{\"createdDate\":\"   \"}";
        Sample sample = jsonMapper.readValue(json, Sample.class);
        assertThat(sample.createdDate()).isNull();
    }

    @Test
    void failsClearlyForMalformedInput() {
        String json = "{\"createdDate\":\"malformed-timestamp\"}";
        assertThatThrownBy(() -> jsonMapper.readValue(json, Sample.class))
                .isInstanceOf(DatabindException.class)
                .hasMessageContaining("Failed to parse Instant from lenient timestamp string");
    }
}
