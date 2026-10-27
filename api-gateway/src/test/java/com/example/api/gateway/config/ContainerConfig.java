/***
<p>
    Licensed under MIT License Copyright (c) 2023-2026 Raja Kolli.
</p>
***/

package com.example.api.gateway.config;

import com.redis.testcontainers.RedisContainer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class ContainerConfig {

    /**
     * Provides a reusable Redis container for integration tests through a Spring service
     * connection.
     *
     * @return the Redis test container configured for reuse
     */
    @Bean
    @ServiceConnection(name = "redis")
    RedisContainer redisContainer() {
        return new RedisContainer(DockerImageName.parse("redis").withTag("8.10.3-alpine"))
                .withReuse(true);
    }
}
