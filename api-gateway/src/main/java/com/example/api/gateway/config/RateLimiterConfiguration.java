/***
<p>
    Licensed under MIT License Copyright (c) 2023-2026 Raja Kolli.
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
        return exchange -> {
            String userId = exchange.getRequest().getHeaders().getFirst("X-User-ID");
            if (userId != null) {
                return Mono.just(userId);
            }
            // Behind a proxy/ingress the remote address (or its resolved IP) can be null.
            var remoteAddress = exchange.getRequest().getRemoteAddress();
            String key = "unknown";
            if (remoteAddress != null) {
                key =
                        remoteAddress.getAddress() != null
                                ? remoteAddress.getAddress().getHostAddress()
                                : remoteAddress.getHostString();
            }
            return Mono.just(key);
        };
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
