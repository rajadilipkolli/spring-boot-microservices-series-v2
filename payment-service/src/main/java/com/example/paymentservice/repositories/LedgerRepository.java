/*** Licensed under MIT License Copyright (c) 2026 Raja Kolli. ***/
package com.example.paymentservice.repositories;

import com.example.paymentservice.jooq.tables.records.LedgerEntriesRecord;
import java.util.Optional;

public interface LedgerRepository {

    /** Returns the ledger entry for the downstream key, or empty if absent. */
    Optional<LedgerEntriesRecord> findByDownstreamKey(String downstreamKey);

    /**
     * Inserts a ledger entry unless its downstream key already exists. Assigns a missing TSID to
     * the supplied record; database failures propagate.
     *
     * @return the persisted entry for the downstream key, preserving an existing entry on replay
     */
    LedgerEntriesRecord save(LedgerEntriesRecord record);
}
