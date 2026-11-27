package com.example.retailstore.webapp.config;

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

    record Sample(
            @JsonDeserialize(using = LenientInstantDeserializer.class)
            Instant createdDate) {}

    @BeforeEach
    void setUp() {
        jsonMapper = JsonMapper.builder().build();
    }

    @Test
    void deserializesOffsetFreeIsoStringAsUtc() throws Exception {
        String json = "{\"createdDate\":\"2026-08-09T11:01:00.157888\"}";
        Sample sample = jsonMapper.readValue(json, Sample.class);
        assertThat(sample.createdDate()).isEqualTo(Instant.parse("2026-08-09T11:01:00.157888Z"));
    }

    @Test
    void deserializesZSuffixedIsoString() throws Exception {
        String json = "{\"createdDate\":\"2026-08-09T11:01:00.157888Z\"}";
        Sample sample = jsonMapper.readValue(json, Sample.class);
        assertThat(sample.createdDate()).isEqualTo(Instant.parse("2026-08-09T11:01:00.157888Z"));
    }

    @Test
    void deserializesNumericOffsetIsoString() throws Exception {
        String json = "{\"createdDate\":\"2026-08-09T11:01:00.157888+05:30\"}";
        Sample sample = jsonMapper.readValue(json, Sample.class);
        assertThat(sample.createdDate()).isEqualTo(Instant.parse("2026-08-09T05:31:00.157888Z"));
    }

    @Test
    void returnsNullForNullInput() throws Exception {
        String json = "{\"createdDate\":null}";
        Sample sample = jsonMapper.readValue(json, Sample.class);
        assertThat(sample.createdDate()).isNull();
    }

    @Test
    void returnsNullForEmptyOrBlankInput() throws Exception {
        String json = "{\"createdDate\":\"   \"}";
        Sample sample = jsonMapper.readValue(json, Sample.class);
        assertThat(sample.createdDate()).isNull();
    }

    @Test
    void failsClearlyForMalformedInput() {
        String json = "{\"createdDate\":\"not-a-valid-date\"}";
        assertThatThrownBy(() -> jsonMapper.readValue(json, Sample.class))
                .isInstanceOf(DatabindException.class)
                .hasMessageContaining("Failed to parse Instant from lenient timestamp string");
    }
}
