/*** Licensed under MIT License Copyright (c) 2026 Raja Kolli. ***/
package com.example.paymentservice.services.provider;

import com.example.paymentservice.config.ApplicationProperties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service
@Primary
public class TimeoutGuardedPaymentProvider implements PaymentProviderPort {

    private static final Logger log = LoggerFactory.getLogger(TimeoutGuardedPaymentProvider.class);

    private final InternalLedgerAdapter delegate;
    private final ApplicationProperties properties;
    private final ExecutorService executorService = Executors.newFixedThreadPool(16);

    public TimeoutGuardedPaymentProvider(
            InternalLedgerAdapter delegate, ApplicationProperties properties) {
        this.delegate = delegate;
        this.properties = properties;
    }

    /**
     * Executes through the ledger adapter, waiting up to the configured provider timeout. A timeout
     * does not cancel the submitted work, so balance effects may finish after this call fails.
     *
     * @return the adapter's payment outcome
     * @throws ProviderTimeoutException if the wait times out
     * @throws RuntimeException if the adapter fails or waiting is interrupted; runtime causes from
     *     the adapter are rethrown and other failures are wrapped
     */
    @Override
    public ProviderResult execute(String downstreamKey, ProviderCommand command) {
        long timeoutMs = properties.getOperation().getProviderTimeout().toMillis();
        try {
            return CompletableFuture.supplyAsync(
                            () -> delegate.execute(downstreamKey, command), executorService)
                    .get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            log.warn("Provider execution timed out for key: {}", downstreamKey);
            throw new ProviderTimeoutException(
                    "Provider execution timed out for downstream key: " + downstreamKey, e);
        } catch (Exception e) {
            log.error("Provider execution failed for key: {}", downstreamKey, e);
            if (e.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException("Provider execution failed", e);
        }
    }

    /**
     * Looks up the ledger outcome, waiting up to the configured provider timeout without cancelling
     * submitted work on timeout.
     *
     * @return the stored payment outcome, or NOT_FOUND with a null outcome if absent
     * @throws ProviderTimeoutException if the wait times out
     * @throws RuntimeException if the adapter fails or waiting is interrupted; runtime causes from
     *     the adapter are rethrown and other failures are wrapped
     */
    @Override
    public ProviderResult lookup(String downstreamKey) {
        long timeoutMs = properties.getOperation().getProviderTimeout().toMillis();
        try {
            return CompletableFuture.supplyAsync(
                            () -> delegate.lookup(downstreamKey), executorService)
                    .get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            log.warn("Provider lookup timed out for key: {}", downstreamKey);
            throw new ProviderTimeoutException(
                    "Provider lookup timed out for downstream key: " + downstreamKey, e);
        } catch (Exception e) {
            log.error("Provider lookup failed for key: {}", downstreamKey, e);
            if (e.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException("Provider lookup failed", e);
        }
    }
}
