/***
<p>
    Licensed under MIT License Copyright (c) 2023-2025 Raja Kolli.
</p>
***/

package com.example.api.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import reactor.core.publisher.Mono;

@Configuration(proxyBeanMethods = false)
class RateLimiterConfiguration {

    @Bean
    KeyResolver userKeyResolver() {
        return exchange ->
                exchange.getRequest().getHeaders().getFirst("X-User-ID") != null
                        ? Mono.just(exchange.getRequest().getHeaders().getFirst("X-User-ID"))
                        : Mono.just(
                                exchange.getRequest()
                                        .getRemoteAddress()
                                        .getAddress()
                                        .getHostAddress());
    }

    @Bean
    RedisTemplate<String, String> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        return template;
    }
}
