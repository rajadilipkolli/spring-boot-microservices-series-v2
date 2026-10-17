/*** Licensed under MIT License Copyright (c) 2026 Raja Kolli. ***/
package com.example.paymentservice.services.provider;

/**
 * Provider SPI port for external or internal payment execution.
 *
 * <p>Implementation note: Downstream keys must remain stable across retries to guarantee safe
 * replay and avoid duplicate ledger effects. When integrating with payment gateways such as Stripe,
 * idempotency keys are retained by the provider for a 24-hour window, requiring durable local
 * recording and lookup before subsequent executions.
 */
public interface PaymentProviderPort {

    /**
     * Executes the payment command with the specified downstream key.
     *
     * @param downstreamKey the idempotent key derived once from the operation
     * @param command the command containing operation details
     * @return the provider result (ACCEPT or REJECT)
     */
    ProviderResult execute(String downstreamKey, ProviderCommand command);

    /**
     * Looks up the status of a previously submitted command by its downstream key.
     *
     * @param downstreamKey the idempotent key
     * @return the provider result, or definitive NOT_FOUND if the provider has never seen the key
     */
    ProviderResult lookup(String downstreamKey);
}
