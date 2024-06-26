/***
<p>
    Licensed under MIT License Copyright (c) 2023-2024 Raja Kolli.
</p>
***/

package com.example.inventoryservice;

import com.example.inventoryservice.common.ContainersConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
@Import(ContainersConfig.class)
public class TestInventoryApplication {

    @Bean
    @ServiceConnection(name = "openzipkin/zipkin")
    GenericContainer<?> zipkContainer() {
        return new GenericContainer<>(DockerImageName.parse("openzipkin/zipkin"))
                .withExposedPorts(9411)
                .withReuse(true);
    }

    @Bean
    @ServiceConnection
    KafkaContainer kafkaContainer() {
        return new KafkaContainer(DockerImageName.parse("apache/kafka").withTag("3.7.0"))
                .withReuse(true);
    }

    public static void main(String[] args) {
        System.setProperty("spring.profiles.active", "test");
        SpringApplication.from(InventoryServiceApplication::main)
                .with(TestInventoryApplication.class)
                .run(args);
    }
}
