/***
<p>
    Licensed under MIT License Copyright (c) 2022-2023 Raja Kolli.
</p>
***/

package com.example.catalogservice.config;

import static io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS;
import static reactor.core.publisher.Mono.just;

import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Configuration(proxyBeanMethods = false)
public class WebClientConfiguration {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private final ApplicationProperties applicationProperties;

    public WebClientConfiguration(ApplicationProperties applicationProperties) {
        this.applicationProperties = applicationProperties;
    }

    @Bean
    WebClient webClient(WebClient.Builder webClientBuilder) {
        return webClientBuilder
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .filter(logRequestDetails())
                .filter(logResponseDetails())
                .baseUrl(applicationProperties.getInventoryServiceUrl())
                .clientConnector(new ReactorClientHttpConnector(clientConnectorConfig()))
                .build();
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
        return ExchangeFilterFunction.ofResponseProcessor(
                clientResponse ->
                        clientResponse
                                .bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .flatMap(
                                        responseBody -> {
                                            final ClientResponse orgClientResponse =
                                                    clientResponse
                                                            .mutate()
                                                            .body(responseBody)
                                                            .build();
                                            log.info(
                                                    "Received response from API with body [{}] status [{}] with response headers [{}]",
                                                    responseBody,
                                                    clientResponse.statusCode(),
                                                    clientResponse
                                                            .headers()
                                                            .asHttpHeaders()
                                                            .toSingleValueMap());
                                            return just(orgClientResponse);
                                        }));
    }

    private HttpClient clientConnectorConfig() {
        return HttpClient.create()
                .option(CONNECT_TIMEOUT_MILLIS, 10_000)
                .doOnConnected(
                        conn -> {
                            conn.addHandlerLast(new ReadTimeoutHandler(15, TimeUnit.SECONDS));
                            conn.addHandlerLast(new WriteTimeoutHandler(10, TimeUnit.SECONDS));
                        });
    }
}
