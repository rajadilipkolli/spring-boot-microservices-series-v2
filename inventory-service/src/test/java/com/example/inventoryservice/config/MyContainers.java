/***
<p>
    Licensed under MIT License Copyright (c) 2023 Raja Kolli.
</p>
***/

package com.example.inventoryservice.config;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

public interface MyContainers {

    @Container @ServiceConnection
    KafkaContainer KAFKA_CONTAINER =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka").withTag("7.5.0"))
                    .withKraft()
                    .withReuse(true);

    @Container
    @ServiceConnection(name = "openzipkin/zipkin")
    GenericContainer<?> zipkinContainer =
            new GenericContainer<>(DockerImageName.parse("openzipkin/zipkin"))
                    .withExposedPorts(9411)
                    .withReuse(true);
}
