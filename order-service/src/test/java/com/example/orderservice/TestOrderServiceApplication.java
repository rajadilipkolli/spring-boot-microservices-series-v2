/* Licensed under Apache-2.0 2023 */
package com.example.orderservice;

import com.example.orderservice.config.MyTestContainers;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.devtools.restart.RestartScope;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
@ImportTestcontainers(MyTestContainers.class)
public class TestOrderServiceApplication {

    @Bean
    @ServiceConnection
    @RestartScope
    KafkaContainer kafkaContainer(DynamicPropertyRegistry propertyRegistry) {
        KafkaContainer kafkaContainer =
                new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka").withTag("7.4.1"))
                        .withKraft();
        propertyRegistry.add("spring.kafka.bootstrap-servers", kafkaContainer::getBootstrapServers);
        propertyRegistry.add(
                "spring.kafka.streams.consumer.bootstrap-servers",
                kafkaContainer::getBootstrapServers);
        propertyRegistry.add(
                "spring.kafka.streams.bootstrap-servers", kafkaContainer::getBootstrapServers);
        return kafkaContainer;
    }

    public static void main(String[] args) {
        SpringApplication.from(OrderServiceApplication::main)
                .with(TestOrderServiceApplication.class)
                .run(args);
    }
}
