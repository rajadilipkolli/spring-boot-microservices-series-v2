/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.api.gateway.web.service;

import com.example.api.gateway.model.ServiceResult;
import com.example.api.gateway.model.ServiceType;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

public abstract class AbstractMicroserviceGenerationStep implements GenerationStep {

    private final ServiceType serviceType;
    private final MicroserviceClient microserviceClient;

    protected AbstractMicroserviceGenerationStep(
            ServiceType serviceType, MicroserviceClient microserviceClient) {
        this.serviceType = serviceType;
        this.microserviceClient = microserviceClient;
    }

    @Override
    public ServiceType getServiceType() {
        return serviceType;
    }

    /**
     * Calls the configured service using the workflow batch ID and optional batch size.
     *
     * @return the service result or a reactive error from the client
     * @throws NullPointerException if the service type or batch ID is null
     * @see MicroserviceClient#call(ServiceType, String, Integer)
     */
    @Override
    public Mono<ServiceResult> execute(String batchId, Integer batchSize) {
        return microserviceClient.call(serviceType, batchId, batchSize);
    }

    @Component
    @Order(1)
    public static class CatalogGenerationStep extends AbstractMicroserviceGenerationStep {
        public CatalogGenerationStep(MicroserviceClient microserviceClient) {
            super(ServiceType.CATALOG, microserviceClient);
        }
    }

    @Component
    @Order(2)
    public static class InventoryGenerationStep extends AbstractMicroserviceGenerationStep {
        public InventoryGenerationStep(MicroserviceClient microserviceClient) {
            super(ServiceType.INVENTORY, microserviceClient);
        }
    }

    @Component
    @Order(3)
    public static class OrderGenerationStep extends AbstractMicroserviceGenerationStep {
        public OrderGenerationStep(MicroserviceClient microserviceClient) {
            super(ServiceType.ORDER, microserviceClient);
        }
    }
}
