/*** Licensed under MIT License Copyright (c) 2026 Raja Kolli. ***/
package com.example.paymentservice.repositories;

import com.example.paymentservice.jooq.tables.records.LedgerEntriesRecord;
import java.util.Optional;

public interface LedgerRepository {

    Optional<LedgerEntriesRecord> findByDownstreamKey(String downstreamKey);

    LedgerEntriesRecord save(LedgerEntriesRecord record);
}
