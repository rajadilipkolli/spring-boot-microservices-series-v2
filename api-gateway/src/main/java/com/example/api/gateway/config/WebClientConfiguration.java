/***
<p>
    Licensed under MIT License Copyright (c) 2023-2025 Raja Kolli.
</p>
***/

package com.example.api.gateway.config;

import io.netty.channel.ChannelOption;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

/** Configures the load-balanced WebClient and its underlying HTTP connection pool. */
@Configuration(proxyBeanMethods = false)
public class WebClientConfiguration {

    private static final String CONNECTION_POOL_NAME = "api-gateway-pool";

    @Bean(destroyMethod = "dispose")
    ConnectionProvider connectionProvider(GatewayProperties properties) {

        return ConnectionProvider.builder(CONNECTION_POOL_NAME)
                .maxConnections(properties.getMaxConnections())
                .pendingAcquireTimeout(properties.getPendingAcquireTimeout())
                .maxIdleTime(properties.getMaxIdleTime())
                .maxLifeTime(properties.getMaxLifeTime())
                .evictInBackground(properties.getEvictInBackground())
                .build();
    }

    @Bean
    HttpClient gatewayHttpClient(
            ConnectionProvider connectionProvider, GatewayProperties properties) {

        return HttpClient.create(connectionProvider)
                .option(
                        ChannelOption.CONNECT_TIMEOUT_MILLIS,
                        Math.toIntExact(properties.getConnectionTimeout().toMillis()))
                .responseTimeout(properties.getResponseTimeout());
    }

    @Bean
    @LoadBalanced
    WebClient.Builder loadBalancedWebClientBuilder(HttpClient gatewayHttpClient) {

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(gatewayHttpClient));
    }

    @Bean
    WebClient webClient(@Qualifier("loadBalancedWebClientBuilder") WebClient.Builder builder) {

        return builder.build();
    }
}
