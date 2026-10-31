/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.api.gateway.web.service;

import com.example.api.gateway.config.GatewayProperties;
import com.example.api.gateway.exception.WorkflowTimeoutException;
import com.example.api.gateway.model.GenerationResponse;
import com.example.api.gateway.model.ServiceResult;
import com.example.api.gateway.model.ServiceType;
import com.example.api.gateway.observability.GatewayMetrics;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class GenerationOrchestrator {

    private final MicroserviceClient microserviceClient;
    private final GatewayProperties properties;
    private final GatewayMetrics gatewayMetrics;

    public GenerationOrchestrator(
            MicroserviceClient microserviceClient,
            GatewayProperties properties,
            GatewayMetrics gatewayMetrics) {

        this.microserviceClient = microserviceClient;
        this.properties = properties;
        this.gatewayMetrics = gatewayMetrics;
    }

    public Mono<GenerationResponse> generate(Integer batchSize) {

        return Mono.defer(
                () -> {
                    String batchId = UUID.randomUUID().toString();

                    return generateWorkflow(batchId, batchSize)
                            .timeout(properties.getWorkflowTimeout())
                            .doOnSuccess(
                                    response ->
                                            gatewayMetrics.incrementWorkflow(
                                                    "success".equalsIgnoreCase(response.status())
                                                            ? "success"
                                                            : "failure"))
                            .doOnError(exception -> gatewayMetrics.incrementWorkflow("failure"))
                            .onErrorMap(TimeoutException.class, WorkflowTimeoutException::new);
                });
    }

    private Mono<GenerationResponse> generateWorkflow(String batchId, Integer batchSize) {

        return microserviceClient
                .call(ServiceType.CATALOG, batchId, batchSize)
                .flatMap(
                        catalog -> {
                            if (!isSuccessful(catalog)) {
                                return Mono.just(failure("Catalog generation failed", catalog));
                            }

                            return delay().then(
                                            microserviceClient.call(
                                                    ServiceType.INVENTORY, batchId, batchSize))
                                    .flatMap(
                                            inventory -> {
                                                if (!isSuccessful(inventory)) {
                                                    return Mono.just(
                                                            failure(
                                                                    "Inventory generation failed",
                                                                    catalog,
                                                                    inventory));
                                                }

                                                return delay().then(
                                                                microserviceClient.call(
                                                                        ServiceType.ORDER,
                                                                        batchId,
                                                                        batchSize))
                                                        .map(
                                                                order ->
                                                                        buildResponse(
                                                                                catalog, inventory,
                                                                                order));
                                            });
                        });
    }

    private boolean isSuccessful(ServiceResult result) {
        return result != null && result.status() == HttpStatus.OK.value();
    }

    private Mono<Void> delay() {
        return Mono.delay(properties.getDelayBetweenServices()).then();
    }

    private GenerationResponse failure(String message, ServiceResult... results) {

        Map<String, String> responses = new java.util.LinkedHashMap<>();

        ServiceType[] services = {ServiceType.CATALOG, ServiceType.INVENTORY, ServiceType.ORDER};

        for (int i = 0; i < results.length; i++) {
            if (results[i] != null) {
                responses.put(
                        services[i].getId(),
                        results[i].response() == null ? "" : results[i].response());
            }
        }

        return new GenerationResponse("error", message, responses);
    }

    private GenerationResponse buildResponse(
            ServiceResult catalog, ServiceResult inventory, ServiceResult order) {

        if (!isSuccessful(order)) {
            return failure("Order generation failed", catalog, inventory, order);
        }

        return new GenerationResponse(
                "success",
                "Generation process completed successfully",
                Map.of(
                        "catalog", catalog.response(),
                        "inventory", inventory.response(),
                        "order", order.response()));
    }
}
