/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.catalogservice.config;

import com.example.catalogservice.entities.OutboxPayload;
import com.example.catalogservice.entities.Product;
import io.r2dbc.postgresql.codec.Json;
import java.util.Arrays;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions;
import org.springframework.data.r2dbc.dialect.PostgresDialect;
import org.springframework.data.r2dbc.mapping.event.AfterSaveCallback;
import reactor.core.publisher.Mono;

@Configuration(proxyBeanMethods = false)
public class R2dbcConfig {

    @Bean
    public R2dbcCustomConversions r2dbcCustomConversions() {
        return R2dbcCustomConversions.of(
                PostgresDialect.INSTANCE,
                Arrays.asList(
                        new OutboxPayloadToPostgresJsonConverter(),
                        new PostgresJsonToOutboxPayloadConverter()));
    }

    @WritingConverter
    static class OutboxPayloadToPostgresJsonConverter implements Converter<OutboxPayload, Json> {
        @Override
        public Json convert(OutboxPayload source) {
            return source.content() != null ? Json.of(source.content()) : null;
        }
    }

    @ReadingConverter
    static class PostgresJsonToOutboxPayloadConverter implements Converter<Json, OutboxPayload> {
        @Override
        public OutboxPayload convert(Json source) {
            return source.asString() != null ? new OutboxPayload(source.asString()) : null;
        }
    }

    @Bean
    public AfterSaveCallback<Product> productAfterSaveCallback() {
        return (product, outboundRow, table) -> {
            product.setNew(false);
            return Mono.just(product);
        };
    }
}
