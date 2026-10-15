/*** Licensed under MIT License Copyright (c) 2026 Raja Kolli. ***/
package com.example.paymentservice.config;

import com.example.paymentservice.jooq.tables.records.CustomersRecord;
import io.hypersistence.tsid.TSID;
import org.jooq.RecordContext;
import org.jooq.RecordListener;

public class CustomerRecordListener implements RecordListener {

    private final TSID.Factory tsidFactory;

    /**
     * Creates a listener that assigns missing customer IDs before insertion.
     *
     * @param tsidFactory the factory used to generate customer IDs
     */
    public CustomerRecordListener(TSID.Factory tsidFactory) {
        this.tsidFactory = tsidFactory;
    }

    /**
     * Assigns a TSID to a customer record with a null ID, preserving supplied IDs and versions.
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
        }
    }
}
