package com.example.retailstore.webapp.config;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdScalarDeserializer;

/**
 * Lenient Jackson 3 deserializer for {@link Instant}.
 *
 * <p>Accepts UTC {@code Z}-suffixed ISO strings, numeric-offset ISO strings (e.g. {@code +05:30}),
 * and offset-free ISO strings (e.g. {@code 2026-01-01T12:00:00}). Offset-free strings are interpreted
 * as UTC. Returns {@code null} for null or empty input, and fails clearly with {@link DatabindException}
 * for malformed input.
 */
public class LenientInstantDeserializer extends StdScalarDeserializer<Instant> {

    public static final LenientInstantDeserializer INSTANCE = new LenientInstantDeserializer();

    public LenientInstantDeserializer() {
        super(Instant.class);
    }

    @Override
    public Instant deserialize(JsonParser p, DeserializationContext ctxt) throws JacksonException {
        if (p.hasToken(JsonToken.VALUE_NULL)) {
            return null;
        }

        String text = p.getValueAsString();
        if (text == null) {
            return null;
        }

        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        try {
            TemporalAccessor parsed =
                    DateTimeFormatter.ISO_DATE_TIME.parseBest(trimmed, Instant::from, LocalDateTime::from);
            if (parsed instanceof Instant instant) {
                return instant;
            } else if (parsed instanceof LocalDateTime localDateTime) {
                return localDateTime.toInstant(ZoneOffset.UTC);
            }
        } catch (DateTimeParseException e) {
            throw DatabindException.from(
                    p, "Failed to parse Instant from lenient timestamp string '" + trimmed + "': " + e.getMessage(), e);
        }

        throw DatabindException.from(p, "Failed to parse Instant from lenient timestamp string: " + trimmed);
    }
}
