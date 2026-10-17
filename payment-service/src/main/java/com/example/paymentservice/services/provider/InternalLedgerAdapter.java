/*** Licensed under MIT License Copyright (c) 2026 Raja Kolli. ***/
package com.example.paymentservice.services.provider;

import com.example.paymentservice.config.ApplicationProperties;
import com.example.paymentservice.jooq.tables.records.LedgerEntriesRecord;
import com.example.paymentservice.repositories.CustomerRepository;
import com.example.paymentservice.repositories.LedgerRepository;
import java.sql.Statement;
import java.util.Optional;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Internal ledger adapter operating in an independent transaction.
 *
 * <p>Ensures stable downstream key replay and records all balance movements in the ledger.
 */
@Service
public class InternalLedgerAdapter implements PaymentProviderPort {

    private static final Logger log = LoggerFactory.getLogger(InternalLedgerAdapter.class);

    private final LedgerRepository ledgerRepository;
    private final CustomerRepository customerRepository;
    private final DSLContext dslContext;
    private final ApplicationProperties properties;

    public InternalLedgerAdapter(
            LedgerRepository ledgerRepository,
            CustomerRepository customerRepository,
            DSLContext dslContext,
            ApplicationProperties properties) {
        this.ledgerRepository = ledgerRepository;
        this.customerRepository = customerRepository;
        this.dslContext = dslContext;
        this.properties = properties;
    }

    /**
     * Reserves, captures, or releases funds and records the outcome in an independent transaction.
     * Serializes concurrent executions by downstream key and replays its stored result without
     * further balance changes. Insufficient funds and unknown operation types yield REJECT;
     * database failures propagate and roll back balance and ledger changes.
     *
     * @param downstreamKey stable identity reused for every retry of the same operation
     * @return ACCEPT on a successful balance update, REJECT otherwise, or the stored replay result
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ProviderResult execute(String downstreamKey, ProviderCommand command) {
        setTimeouts();
        dslContext.fetch("select pg_advisory_xact_lock(hashtextextended(?, 0))", downstreamKey);

        Optional<LedgerEntriesRecord> existing =
                ledgerRepository.findByDownstreamKey(downstreamKey);
        if (existing.isPresent()) {
            log.info(
                    "Ledger entry already exists for key: {}, returning stored result",
                    downstreamKey);
            return "ACCEPT".equals(existing.get().getStatus())
                    ? ProviderResult.accept()
                    : ProviderResult.reject();
        }

        boolean success;
        if ("RESERVE".equalsIgnoreCase(command.operationType())) {
            success = customerRepository.reserveFunds(command.customerId(), command.amount());
        } else if ("CAPTURE".equalsIgnoreCase(command.operationType())) {
            success = customerRepository.captureFunds(command.customerId(), command.amount());
        } else if ("RELEASE".equalsIgnoreCase(command.operationType())) {
            success = customerRepository.releaseFunds(command.customerId(), command.amount());
        } else {
            log.warn("Unknown operation type: {}", command.operationType());
            success = false;
        }

        String status = success ? "ACCEPT" : "REJECT";
        LedgerEntriesRecord entry = new LedgerEntriesRecord();
        entry.setCustomerId(command.customerId());
        entry.setOperationId(command.operationId());
        entry.setDownstreamKey(downstreamKey);
        entry.setEntryType(command.operationType());
        entry.setAmount(command.amount());
        entry.setStatus(status);

        ledgerRepository.save(entry);

        return success ? ProviderResult.accept() : ProviderResult.reject();
    }

    /**
     * Returns the stored ACCEPT or REJECT outcome for the downstream key, or NOT_FOUND with a null
     * outcome when no ledger entry exists. Database lookup failures propagate.
     */
    @Override
    @Transactional(readOnly = true)
    public ProviderResult lookup(String downstreamKey) {
        setTimeouts();
        Optional<LedgerEntriesRecord> existing =
                ledgerRepository.findByDownstreamKey(downstreamKey);
        if (existing.isPresent()) {
            return "ACCEPT".equals(existing.get().getStatus())
                    ? ProviderResult.accept()
                    : ProviderResult.reject();
        }
        return ProviderResult.notFound();
    }

    /**
     * Applies configured statement and lock timeouts in milliseconds to the current PostgreSQL
     * transaction. Configuration failures are caught and execution continues.
     */
    private void setTimeouts() {
        try {
            long statementMs = properties.getOperation().getStatementTimeout().toMillis();
            long lockMs = properties.getOperation().getLockTimeout().toMillis();
            dslContext.connection(
                    connection -> {
                        try (Statement stmt = connection.createStatement()) {
                            stmt.execute("SET LOCAL statement_timeout = " + statementMs);
                            stmt.execute("SET LOCAL lock_timeout = " + lockMs);
                        }
                    });
        } catch (Exception e) {
            log.debug("Could not set PostgreSQL session timeouts (e.g. non-postgres dialect)", e);
        }
    }
}
