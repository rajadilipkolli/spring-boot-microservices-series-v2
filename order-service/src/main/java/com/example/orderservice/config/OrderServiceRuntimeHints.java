/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.orderservice.config;

import com.example.orderservice.entities.OrderItem;
import com.example.orderservice.mapper.OrderMapperDecorator;
import com.example.orderservice.model.Address;
import com.example.orderservice.model.response.OrderItemResponse;
import com.example.orderservice.model.response.OrderResponse;
import com.example.orderservice.services.OrderService;
import java.lang.reflect.Method;
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
import org.springframework.aot.hint.ExecutableMode;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.springframework.kafka.streams.RecoveringDeserializationExceptionHandler;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonSerde;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import org.springframework.util.ReflectionUtils;

public class OrderServiceRuntimeHints implements RuntimeHintsRegistrar {

    /**
     * Registers reflection and proxy hints for order-service native images, including JobRunr job
     * handlers, MapStruct decorators, Kafka Streams exception handlers, JPA auditing listeners,
     * Liquibase preconditions, Kafka Serdes, assignors, and Hibernate proxies. Also registers
     * serialization hints for serialized lambdas and resource patterns for Liquibase, JobRunr,
     * and Spring Modulith.
     *
     * @param hints registry to update
     * @param classLoader unused by this registrar
     */
    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        hints.reflection()
                .registerType(
                        OrderResponse.class,
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.ACCESS_DECLARED_FIELDS)
                .registerType(
                        Address.class,
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.ACCESS_DECLARED_FIELDS)
                .registerType(
                        OrderItemResponse.class,
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.ACCESS_DECLARED_FIELDS)
                .registerType(
                        OrderService.class,
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.ACCESS_DECLARED_FIELDS)
                .registerType(
                        RecoveringDeserializationExceptionHandler.class,
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.ACCESS_DECLARED_FIELDS)
                .registerType(
                        OrderMapperDecorator.class,
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.ACCESS_DECLARED_FIELDS)
                .registerType(
                        AuditingEntityListener.class,
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.ACCESS_DECLARED_FIELDS)
                .registerType(
                        OrderItem.class,
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.ACCESS_DECLARED_FIELDS)
                .registerType(
                        TypeReference.of("org.hibernate.proxy.HibernateProxy"),
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.ACCESS_DECLARED_FIELDS)
                .registerType(
                        TypeReference.of("liquibase.precondition.core.ColumnExistsPrecondition"),
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.ACCESS_DECLARED_FIELDS)
                .registerType(
                        TypeReference.of("liquibase.precondition.core.TableExistsPrecondition"),
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.ACCESS_DECLARED_FIELDS)
                .registerType(
                        ByteArrayDeserializer.class,
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.ACCESS_DECLARED_FIELDS)
                .registerType(
                        ByteArraySerializer.class,
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.ACCESS_DECLARED_FIELDS)
                .registerType(
                        StringDeserializer.class,
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.ACCESS_DECLARED_FIELDS)
                .registerType(
                        StringSerializer.class,
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.ACCESS_DECLARED_FIELDS)
                .registerType(
                        Serdes.StringSerde.class,
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.ACCESS_DECLARED_FIELDS)
                .registerType(
                        JacksonJsonSerde.class,
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.ACCESS_DECLARED_FIELDS)
                .registerType(
                        JacksonJsonDeserializer.class,
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.ACCESS_DECLARED_FIELDS)
                .registerType(
                        JacksonJsonSerializer.class,
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.ACCESS_DECLARED_FIELDS)
                .registerType(
                        WallclockTimestampExtractor.class,
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.ACCESS_DECLARED_FIELDS)
                .registerType(
                        StreamsPartitionAssignor.class,
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.ACCESS_DECLARED_FIELDS)
                .registerType(
                        HighAvailabilityTaskAssignor.class,
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.ACCESS_DECLARED_FIELDS)
                .registerType(
                        LegacyStickyTaskAssignor.class,
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.ACCESS_DECLARED_FIELDS)
                .registerType(
                        FallbackPriorTaskAssignor.class,
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.ACCESS_DECLARED_FIELDS)
                .registerType(
                        StickyTaskAssignor.class,
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.ACCESS_DECLARED_FIELDS)
                .registerType(
                        RangeAssignor.class,
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.ACCESS_DECLARED_FIELDS)
                .registerType(
                        RoundRobinAssignor.class,
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.ACCESS_DECLARED_FIELDS)
                .registerType(
                        StickyAssignor.class,
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.ACCESS_DECLARED_FIELDS)
                .registerType(
                        CooperativeStickyAssignor.class,
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.ACCESS_DECLARED_FIELDS)
                .registerType(
                        DatabaseCreator.class,
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.ACCESS_DECLARED_FIELDS)
                .registerType(
                        SqlMigrationByPath.class,
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.ACCESS_DECLARED_FIELDS)
                .registerType(
                        DefaultSqlMigrationProvider.class,
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.ACCESS_DECLARED_FIELDS)
                .registerType(
                        DatabaseMigrationsProvider.class,
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.ACCESS_DECLARED_FIELDS)
                .registerType(
                        TypeReference.of(
                                "org.springframework.modulith.events.jobrunr.JobRunrEventExternalizer"),
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.ACCESS_DECLARED_FIELDS)
                .registerType(
                        TypeReference.of(
                                "org.springframework.modulith.events.jobrunr.JobRunrExternalizationTransport"),
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.ACCESS_DECLARED_FIELDS)
                .registerType(
                        TypeReference.of(
                                "org.springframework.modulith.events.kafka."
                                        + "KafkaEventExternalizerConfiguration$KafkaOutboxConfiguration$JobRunrOutboxAutoConfiguration"),
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.ACCESS_DECLARED_FIELDS);

        hints.serialization().registerType(TypeReference.of("java.lang.invoke.SerializedLambda"));

        Method retryNewOrdersMethod =
                ReflectionUtils.findMethod(OrderService.class, "retryNewOrders");
        if (retryNewOrdersMethod != null) {
            hints.reflection().registerMethod(retryNewOrdersMethod, ExecutableMode.INVOKE);
        }

        hints.proxies()
                .registerJdkProxy(TypeReference.of("org.hibernate.proxy.HibernateProxy"))
                .registerJdkProxy(
                        TypeReference.of("org.hibernate.proxy.HibernateProxy"),
                        TypeReference.of("org.springframework.aop.SpringProxy"),
                        TypeReference.of("org.springframework.aop.framework.Advised"),
                        TypeReference.of("org.springframework.core.DecoratingProxy"));

        hints.resources()
                .registerPattern("www.liquibase.org/xml/ns/dbchangelog/*")
                .registerPattern("org/jobrunr/storage/sql/common/migrations/*")
                .registerPattern("org/jobrunr/storage/sql/postgres/migrations/*")
                .registerPattern(
                        "org/springframework/modulith/events/jobrunr/JobRunrEventExternalizer.class")
                .registerPattern("org/springframework/modulith/*");
    }
}
