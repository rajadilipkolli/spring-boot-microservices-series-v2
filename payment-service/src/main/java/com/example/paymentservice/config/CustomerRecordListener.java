/*** Licensed under MIT License Copyright (c) 2026 Raja Kolli. ***/
package com.example.paymentservice.config;

import com.example.paymentservice.jooq.tables.records.CustomersRecord;
import io.hypersistence.tsid.TSID;
import org.jooq.RecordContext;
import org.jooq.RecordListener;

public class CustomerRecordListener implements RecordListener {

    private final TSID.Factory tsidFactory;

    public CustomerRecordListener(TSID.Factory tsidFactory) {
        this.tsidFactory = tsidFactory;
    }

    @Override
    public void insertStart(RecordContext ctx) {
        if (ctx.record() instanceof CustomersRecord customerRecord) {
            if (customerRecord.getId() == null) {
                customerRecord.setId(tsidFactory.generate().toLong());
            }
        }
    }
}
