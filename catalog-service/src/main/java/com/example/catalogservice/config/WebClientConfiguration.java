/***
<p>
    Licensed under MIT License Copyright (c) 2021-2025 Raja Kolli.
</p>
***/

package com.example.catalogservice.config;

import static io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS;
import static reactor.core.publisher.Mono.just;

import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;
import reactor.util.retry.Retry;

@Configuration(proxyBeanMethods = false)
public class WebClientConfiguration {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private final ApplicationProperties applicationProperties;

    public WebClientConfiguration(ApplicationProperties applicationProperties) {
        this.applicationProperties = applicationProperties;
    }

    @Bean
    WebClient webClient(WebClient.Builder webClientBuilder) {
        return webClientBuilder.baseUrl(applicationProperties.inventoryServiceUrl()).build();
    }

    @Bean
    WebClientCustomizer webClientCustomizer() {
        return webClientBuilder ->
                webClientBuilder
                        .defaultHeaders(
                                httpHeaders -> {
                                    httpHeaders.add(
                                            HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
                                    httpHeaders.add(
                                            HttpHeaders.CONTENT_TYPE,
                                            MediaType.APPLICATION_JSON_VALUE);
                                })
                        .filter(logRequestDetails())
                        .filter(logResponseDetails())
                        .filter(
                                (request, next) ->
                                        next.exchange(request)
                                                .retryWhen(
                                                        Retry.backoff(3, Duration.ofMillis(100))))
                        .clientConnector(new ReactorClientHttpConnector(clientConnectorConfig()));
    }

    private ExchangeFilterFunction logRequestDetails() {
        return ExchangeFilterFunction.ofRequestProcessor(
                clientRequest -> {
                    log.info(
                            "Sending [{}] request to URL [{}] with request headers [{}]",
                            clientRequest.method(),
                            clientRequest.url(),
                            clientRequest.headers());
                    return just(clientRequest);
                });
    }

    private ExchangeFilterFunction logResponseDetails() {
        // Avoid attempting to eagerly read the body as a String. Reading
        // binary/gzipped payloads here can corrupt the reactive pipeline
        // and lead to JSON parsing errors downstream. Instead, log status
        // and headers only and return the original response unchanged.
        return ExchangeFilterFunction.ofResponseProcessor(
                clientResponse -> {
                    log.info(
                            "Received response from API with status [{}] and response headers [{}]",
                            clientResponse.statusCode(),
                            clientResponse.headers().asHttpHeaders().toSingleValueMap());
                    return just(clientResponse);
                });
    }

    private HttpClient clientConnectorConfig() {
        ConnectionProvider connectionProvider =
                ConnectionProvider.builder("custom")
                        .maxConnections(10)
                        .pendingAcquireMaxCount(50)
                        .maxIdleTime(Duration.ofSeconds(20))
                        .maxLifeTime(Duration.ofSeconds(60))
                        .build();

        return HttpClient.create(connectionProvider)
                .option(CONNECT_TIMEOUT_MILLIS, 10_000)
                .responseTimeout(Duration.ofSeconds(10))
                .doOnConnected(
                        conn -> {
                            conn.addHandlerLast(new ReadTimeoutHandler(15, TimeUnit.SECONDS));
                            conn.addHandlerLast(new WriteTimeoutHandler(10, TimeUnit.SECONDS));
                        });
    }
}
