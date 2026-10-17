/*** Licensed under MIT License Copyright (c) 2026 Raja Kolli. ***/
package com.example.paymentservice.config;

import com.example.paymentservice.jooq.tables.records.CustomersRecord;
import com.example.paymentservice.jooq.tables.records.LedgerEntriesRecord;
import com.example.paymentservice.jooq.tables.records.OutboxEventsRecord;
import com.example.paymentservice.jooq.tables.records.PaymentOperationsRecord;
import io.hypersistence.tsid.TSID;
import org.jooq.RecordContext;
import org.jooq.RecordListener;

public class TsidRecordListener implements RecordListener {

    private final TSID.Factory tsidFactory;

    /**
     * Creates a listener that assigns missing TSIDs before record insertion.
     *
     * @param tsidFactory the factory used to generate TSIDs
     */
    public TsidRecordListener(TSID.Factory tsidFactory) {
        this.tsidFactory = tsidFactory;
    }

    /**
     * Assigns a TSID to any supported entity record that has a null ID, preserving supplied IDs.
     * Records of other types are ignored.
     *
     * @param ctx the insertion context containing the record to inspect
     */
    @Override
    public void insertStart(RecordContext ctx) {
        if (ctx.record() instanceof CustomersRecord customerRecord) {
            if (customerRecord.getId() == null) {
                customerRecord.setId(tsidFactory.generate().toLong());
            }
        } else if (ctx.record() instanceof PaymentOperationsRecord operationRecord) {
            if (operationRecord.getId() == null) {
                operationRecord.setId(tsidFactory.generate().toLong());
            }
        } else if (ctx.record() instanceof LedgerEntriesRecord ledgerRecord) {
            if (ledgerRecord.getId() == null) {
                ledgerRecord.setId(tsidFactory.generate().toLong());
            }
        } else if (ctx.record() instanceof OutboxEventsRecord outboxRecord) {
            if (outboxRecord.getId() == null) {
                outboxRecord.setId(tsidFactory.generate().toLong());
            }
        }
    }
}
