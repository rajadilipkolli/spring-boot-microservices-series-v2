/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.orderservice.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.orderservice.entities.OrderItem;
import com.example.orderservice.mapper.OrderMapperDecorator;
import com.example.orderservice.model.Address;
import com.example.orderservice.model.response.OrderItemResponse;
import com.example.orderservice.model.response.OrderResponse;
import com.example.orderservice.services.OrderService;
import org.apache.kafka.clients.consumer.CooperativeStickyAssignor;
import org.apache.kafka.clients.consumer.RangeAssignor;
import org.apache.kafka.clients.consumer.RoundRobinAssignor;
import org.apache.kafka.clients.consumer.StickyAssignor;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.processor.WallclockTimestampExtractor;
import org.apache.kafka.streams.processor.assignment.assignors.StickyTaskAssignor;
import org.apache.kafka.streams.processor.internals.StreamsPartitionAssignor;
import org.apache.kafka.streams.processor.internals.assignment.FallbackPriorTaskAssignor;
import org.apache.kafka.streams.processor.internals.assignment.HighAvailabilityTaskAssignor;
import org.apache.kafka.streams.processor.internals.assignment.LegacyStickyTaskAssignor;
import org.jobrunr.storage.sql.common.DatabaseCreator;
import org.jobrunr.storage.sql.common.DatabaseMigrationsProvider;
import org.jobrunr.storage.sql.common.migrations.DefaultSqlMigrationProvider;
import org.jobrunr.storage.sql.common.migrations.SqlMigrationByPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledInNativeImage;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeHint;
import org.springframework.aot.hint.TypeReference;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.springframework.kafka.streams.RecoveringDeserializationExceptionHandler;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonSerde;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;

@DisabledInNativeImage
class OrderServiceRuntimeHintsTest {

    private final OrderServiceRuntimeHints registrar = new OrderServiceRuntimeHints();

    @Test
    void registersAllRequiredRuntimeHints() {
        RuntimeHints hints = new RuntimeHints();
        registrar.registerHints(hints, getClass().getClassLoader());

        assertThat(RuntimeHintsPredicates.reflection().onType(OrderResponse.class)).accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection().onType(Address.class)).accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection().onType(OrderItemResponse.class))
                .accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection().onType(OrderService.class)).accepts(hints);
        assertThat(
                        RuntimeHintsPredicates.reflection()
                                .onMethodInvocation(OrderService.class, "retryNewOrders"))
                .accepts(hints);
        assertThat(
                        RuntimeHintsPredicates.reflection()
                                .onType(RecoveringDeserializationExceptionHandler.class))
                .accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection().onType(OrderMapperDecorator.class))
                .accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection().onType(AuditingEntityListener.class))
                .accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection().onType(OrderItem.class)).accepts(hints);
        assertThat(
                        RuntimeHintsPredicates.reflection()
                                .onType(TypeReference.of("org.hibernate.proxy.HibernateProxy")))
                .accepts(hints);
        assertThat(
                        RuntimeHintsPredicates.reflection()
                                .onType(
                                        TypeReference.of(
                                                "liquibase.precondition.core.ColumnExistsPrecondition")))
                .accepts(hints);
        assertThat(
                        RuntimeHintsPredicates.reflection()
                                .onType(
                                        TypeReference.of(
                                                "liquibase.precondition.core.TableExistsPrecondition")))
                .accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection().onType(ByteArrayDeserializer.class))
                .accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection().onType(ByteArraySerializer.class))
                .accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection().onType(StringDeserializer.class))
                .accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection().onType(StringSerializer.class))
                .accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection().onType(Serdes.StringSerde.class))
                .accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection().onType(JacksonJsonSerde.class))
                .accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection().onType(JacksonJsonDeserializer.class))
                .accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection().onType(JacksonJsonSerializer.class))
                .accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection().onType(WallclockTimestampExtractor.class))
                .accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection().onType(StreamsPartitionAssignor.class))
                .accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection().onType(HighAvailabilityTaskAssignor.class))
                .accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection().onType(LegacyStickyTaskAssignor.class))
                .accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection().onType(FallbackPriorTaskAssignor.class))
                .accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection().onType(StickyTaskAssignor.class))
                .accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection().onType(RangeAssignor.class)).accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection().onType(RoundRobinAssignor.class))
                .accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection().onType(StickyAssignor.class)).accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection().onType(CooperativeStickyAssignor.class))
                .accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection().onType(DatabaseCreator.class))
                .accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection().onType(SqlMigrationByPath.class))
                .accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection().onType(DefaultSqlMigrationProvider.class))
                .accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection().onType(DatabaseMigrationsProvider.class))
                .accepts(hints);

        assertThat(hints.reflection().typeHints())
                .map(TypeHint::getType)
                .contains(
                        TypeReference.of(OrderResponse.class),
                        TypeReference.of(Address.class),
                        TypeReference.of(OrderItemResponse.class),
                        TypeReference.of(OrderService.class),
                        TypeReference.of(RecoveringDeserializationExceptionHandler.class),
                        TypeReference.of(OrderMapperDecorator.class),
                        TypeReference.of(AuditingEntityListener.class),
                        TypeReference.of(OrderItem.class),
                        TypeReference.of("org.hibernate.proxy.HibernateProxy"),
                        TypeReference.of("liquibase.precondition.core.ColumnExistsPrecondition"),
                        TypeReference.of("liquibase.precondition.core.TableExistsPrecondition"),
                        TypeReference.of(ByteArrayDeserializer.class),
                        TypeReference.of(ByteArraySerializer.class),
                        TypeReference.of(StringDeserializer.class),
                        TypeReference.of(StringSerializer.class),
                        TypeReference.of(Serdes.StringSerde.class),
                        TypeReference.of(JacksonJsonSerde.class),
                        TypeReference.of(JacksonJsonDeserializer.class),
                        TypeReference.of(JacksonJsonSerializer.class),
                        TypeReference.of(WallclockTimestampExtractor.class),
                        TypeReference.of(StreamsPartitionAssignor.class),
                        TypeReference.of(HighAvailabilityTaskAssignor.class),
                        TypeReference.of(LegacyStickyTaskAssignor.class),
                        TypeReference.of(FallbackPriorTaskAssignor.class),
                        TypeReference.of(StickyTaskAssignor.class),
                        TypeReference.of(RangeAssignor.class),
                        TypeReference.of(RoundRobinAssignor.class),
                        TypeReference.of(StickyAssignor.class),
                        TypeReference.of(CooperativeStickyAssignor.class),
                        TypeReference.of(DatabaseCreator.class),
                        TypeReference.of(SqlMigrationByPath.class),
                        TypeReference.of(DefaultSqlMigrationProvider.class),
                        TypeReference.of(DatabaseMigrationsProvider.class),
                        TypeReference.of(
                                "org.springframework.modulith.events.jobrunr.JobRunrEventExternalizer"),
                        TypeReference.of(
                                "org.springframework.modulith.events.jobrunr.JobRunrExternalizationTransport"),
                        TypeReference.of(
                                "org.springframework.modulith.events.kafka."
                                        + "KafkaEventExternalizerConfiguration$KafkaOutboxConfiguration$JobRunrOutboxAutoConfiguration"));

        assertThat(
                        RuntimeHintsPredicates.serialization()
                                .onType(TypeReference.of("java.lang.invoke.SerializedLambda")))
                .accepts(hints);

        assertThat(hints.proxies().jdkProxyHints()).isNotEmpty();
        assertThat(
                        RuntimeHintsPredicates.resource()
                                .forResource(
                                        "www.liquibase.org/xml/ns/dbchangelog/dbchangelog-5.0.xsd"))
                .accepts(hints);
        assertThat(
                        RuntimeHintsPredicates.resource()
                                .forResource(
                                        "org/jobrunr/storage/sql/common/migrations/v000__create_migrations_table.sql"))
                .accepts(hints);
        assertThat(
                        RuntimeHintsPredicates.resource()
                                .forResource(
                                        "org/jobrunr/storage/sql/postgres/migrations/v014__improve_job_stats.sql"))
                .accepts(hints);
        assertThat(
                        RuntimeHintsPredicates.resource()
                                .forResource(
                                        "org/springframework/modulith/events/jobrunr/JobRunrEventExternalizer.class"))
                .accepts(hints);
    }
}
