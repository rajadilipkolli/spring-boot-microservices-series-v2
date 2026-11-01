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
import com.example.api.gateway.observability.GatewayMetrics;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class GenerationOrchestrator {

    private final List<GenerationStep> generationSteps;
    private final GatewayProperties properties;
    private final GatewayMetrics gatewayMetrics;

    @Autowired
    public GenerationOrchestrator(
            List<GenerationStep> generationSteps,
            GatewayProperties properties,
            GatewayMetrics gatewayMetrics) {
        this.generationSteps = List.copyOf(generationSteps);
        this.properties = properties;
        this.gatewayMetrics = gatewayMetrics;
    }

    public GenerationOrchestrator(
            MicroserviceClient microserviceClient,
            GatewayProperties properties,
            GatewayMetrics gatewayMetrics) {
        this(
                List.of(
                        new AbstractMicroserviceGenerationStep.CatalogGenerationStep(
                                microserviceClient),
                        new AbstractMicroserviceGenerationStep.InventoryGenerationStep(
                                microserviceClient),
                        new AbstractMicroserviceGenerationStep.OrderGenerationStep(
                                microserviceClient)),
                properties,
                gatewayMetrics);
    }

    /**
     * Runs generation steps in sequence, stopping after an unsuccessful result. Each subscription
     * uses a new batch ID and records the workflow outcome.
     *
     * @param batchSize optional batch size passed to each service
     * @return a response containing the service results, or an error if a call fails; the workflow
     *     deadline is reported as a {@link WorkflowTimeoutException}
     */
    public Mono<GenerationResponse> generate(Integer batchSize) {
        return Mono.defer(
                () -> {
                    String batchId = UUID.randomUUID().toString();

                    return executeSteps(batchId, batchSize, 0, new LinkedHashMap<>())
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

    private Mono<GenerationResponse> executeSteps(
            String batchId,
            Integer batchSize,
            int stepIndex,
            Map<String, String> accumulatedResponses) {
        if (stepIndex >= generationSteps.size()) {
            return Mono.just(
                    new GenerationResponse(
                            "success",
                            "Generation process completed successfully",
                            accumulatedResponses));
        }

        GenerationStep step = generationSteps.get(stepIndex);
        return step.execute(batchId, batchSize)
                .flatMap(
                        result -> {
                            String serviceKey = step.getServiceType().getId();
                            String responseBody =
                                    result.response() == null ? "" : result.response();
                            accumulatedResponses.put(serviceKey, responseBody);

                            if (!isSuccessful(result)) {
                                String failureMsg =
                                        step.getServiceType().getDisplayName()
                                                + " generation failed";
                                return Mono.just(
                                        new GenerationResponse(
                                                "error", failureMsg, accumulatedResponses));
                            }

                            return delay().then(
                                            executeSteps(
                                                    batchId,
                                                    batchSize,
                                                    stepIndex + 1,
                                                    accumulatedResponses));
                        });
    }

    private boolean isSuccessful(ServiceResult result) {
        return result != null && HttpStatus.valueOf(result.status()).is2xxSuccessful();
    }

    private Mono<Void> delay() {
        return Mono.delay(properties.getDelayBetweenServices()).then();
    }
}
