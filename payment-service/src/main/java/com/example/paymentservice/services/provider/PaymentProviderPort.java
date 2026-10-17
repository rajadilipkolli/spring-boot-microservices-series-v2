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
     * @throws ProviderTimeoutException if provider execution exceeds its timeout; the outcome may
     *     still be unknown and should be looked up using the same key
     * @throws RuntimeException if execution fails rather than producing a payment outcome
     */
    ProviderResult execute(String downstreamKey, ProviderCommand command);

    /**
     * Looks up the status of a previously submitted command by its downstream key.
     *
     * @param downstreamKey the idempotent key
     * @return the stored provider result, or NOT_FOUND with a null outcome if no stored result is
     *     found
     * @throws ProviderTimeoutException if lookup exceeds its timeout
     * @throws RuntimeException if lookup fails; a failed lookup is not a NOT_FOUND result
     */
    ProviderResult lookup(String downstreamKey);
}
