/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.catalogservice.config;

import io.r2dbc.postgresql.codec.Json;
import io.r2dbc.spi.ConnectionFactory;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions;
import org.springframework.data.r2dbc.dialect.DialectResolver;
import org.springframework.data.r2dbc.dialect.R2dbcDialect;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
public class R2dbcConfig {

    @Bean
    R2dbcCustomConversions r2dbcCustomConversions(
            ConnectionFactory connectionFactory, JsonMapper jsonMapper) {
        R2dbcDialect dialect = DialectResolver.getDialect(connectionFactory);
        List<Object> converters = new ArrayList<>(dialect.getConverters());
        converters.add(new JsonNodeToPostgresJsonConverter());
        converters.add(new PostgresJsonToJsonNodeConverter(jsonMapper));
        return R2dbcCustomConversions.of(dialect, converters);
    }

    @WritingConverter
    public static class JsonNodeToPostgresJsonConverter implements Converter<JsonNode, Json> {
        @Override
        public Json convert(JsonNode source) {
            return Json.of(source.toString());
        }
    }

    @ReadingConverter
    public static class PostgresJsonToJsonNodeConverter implements Converter<Json, JsonNode> {
        private final JsonMapper jsonMapper;

        public PostgresJsonToJsonNodeConverter(JsonMapper jsonMapper) {
            this.jsonMapper = jsonMapper;
        }

        @Override
        public JsonNode convert(Json source) {
            try {
                return jsonMapper.readTree(source.asString());
            } catch (Exception e) {
                return null;
            }
        }
    }
}
