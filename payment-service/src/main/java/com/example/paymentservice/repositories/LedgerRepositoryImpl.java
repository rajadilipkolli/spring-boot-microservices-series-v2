/*** Licensed under MIT License Copyright (c) 2026 Raja Kolli. ***/
package com.example.paymentservice.repositories;

import static com.example.paymentservice.jooq.tables.LedgerEntries.LEDGER_ENTRIES;

import com.example.paymentservice.jooq.tables.records.LedgerEntriesRecord;
import io.hypersistence.tsid.TSID;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repository implementation for managing {@link LedgerEntriesRecord} entities using jOOQ.
 *
 * <p>Note: jOOQ's {@code RecordListener} SPI only intercepts active-record CRUD operations (such as
 * {@code UpdatableRecord.insert()}). Because this repository uses fluent DSL queries with {@code
 * onConflict(...).doNothing()} (e.g. {@code dslContext.insertInto(...).set(record)...}), {@code
 * RecordListener} lifecycle events like {@code insertStart()} are not triggered. Therefore, missing
 * TSID primary keys must be assigned explicitly via {@link TSID.Factory} prior to statement
 * execution.
 */
@Repository
public class LedgerRepositoryImpl implements LedgerRepository {

    private final DSLContext dslContext;
    private final TSID.Factory tsidFactory;

    public LedgerRepositoryImpl(DSLContext dslContext, TSID.Factory tsidFactory) {
        this.dslContext = dslContext;
        this.tsidFactory = tsidFactory;
    }

    @Override
    public Optional<LedgerEntriesRecord> findByDownstreamKey(String downstreamKey) {
        return dslContext
                .selectFrom(LEDGER_ENTRIES)
                .where(LEDGER_ENTRIES.DOWNSTREAM_KEY.eq(downstreamKey))
                .fetchOptional();
    }

    @Override
    @Transactional
    public LedgerEntriesRecord save(LedgerEntriesRecord record) {
        if (record.getId() == null) {
            record.setId(tsidFactory.generate().toLong());
        }
        dslContext
                .insertInto(LEDGER_ENTRIES)
                .set(record)
                .onConflict(LEDGER_ENTRIES.DOWNSTREAM_KEY)
                .doNothing()
                .execute();

        return dslContext
                .selectFrom(LEDGER_ENTRIES)
                .where(LEDGER_ENTRIES.DOWNSTREAM_KEY.eq(record.getDownstreamKey()))
                .fetchOne();
    }
}
