/*** Licensed under MIT License Copyright (c) 2026 Raja Kolli. ***/
package com.example.paymentservice.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.example.paymentservice.jooq.tables.records.CustomersRecord;
import com.example.paymentservice.jooq.tables.records.LedgerEntriesRecord;
import com.example.paymentservice.jooq.tables.records.OutboxEventsRecord;
import com.example.paymentservice.jooq.tables.records.PaymentOperationsRecord;
import io.hypersistence.tsid.TSID;
import org.jooq.Record;
import org.jooq.RecordContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TsidRecordListenerTest {

    private TsidRecordListener listener;
    private RecordContext recordContext;

    /** Creates a TSID listener with node one and a mocked record context for each test. */
    @BeforeEach
    void setUp() {
        TSID.Factory tsidFactory = TSID.Factory.builder().withNode(1).build();
        listener = new TsidRecordListener(tsidFactory);
        recordContext = mock(RecordContext.class);
    }

    /** Verifies that a customer without an ID receives a TSID before insertion. */
    @Test
    void assignsTsidWhenCustomerRecordIdIsNull() {
        CustomersRecord record = new CustomersRecord();
        record.setName("John Doe");
        record.setEmail("john@example.com");
        given(recordContext.record()).willReturn(record);

        listener.insertStart(recordContext);

        assertThat(record.getId()).isNotNull();
    }

    /** Verifies that a payment operation without an ID receives a TSID before insertion. */
    @Test
    void assignsTsidWhenPaymentOperationsRecordIdIsNull() {
        PaymentOperationsRecord record = new PaymentOperationsRecord();
        record.setOperationKey("op-1");
        given(recordContext.record()).willReturn(record);

        listener.insertStart(recordContext);

        assertThat(record.getId()).isNotNull();
    }

    /** Verifies that a ledger entry without an ID receives a TSID before insertion. */
    @Test
    void assignsTsidWhenLedgerEntriesRecordIdIsNull() {
        LedgerEntriesRecord record = new LedgerEntriesRecord();
        record.setDownstreamKey("downstream-1");
        given(recordContext.record()).willReturn(record);

        listener.insertStart(recordContext);

        assertThat(record.getId()).isNotNull();
    }

    /** Verifies that an outbox event without an ID receives a TSID before insertion. */
    @Test
    void assignsTsidWhenOutboxEventsRecordIdIsNull() {
        OutboxEventsRecord record = new OutboxEventsRecord();
        record.setAggregateId("agg-1");
        given(recordContext.record()).willReturn(record);

        listener.insertStart(recordContext);

        assertThat(record.getId()).isNotNull();
    }

    /** Verifies that insertion preserves an explicitly supplied customer ID. */
    @Test
    void preservesSuppliedId() {
        long suppliedId = 123456789L;
        CustomersRecord record = new CustomersRecord();
        record.setId(suppliedId);
        record.setName("Jane Doe");
        given(recordContext.record()).willReturn(record);

        listener.insertStart(recordContext);

        assertThat(record.getId()).isEqualTo(suppliedId);
    }

    /** Verifies that ID assignment leaves both null and explicit customer versions unchanged. */
    @Test
    void preservesNullAndSuppliedVersions() {
        CustomersRecord recordWithNullVersion = new CustomersRecord();
        given(recordContext.record()).willReturn(recordWithNullVersion);

        listener.insertStart(recordContext);

        assertThat(recordWithNullVersion.getVersion()).isNull();

        CustomersRecord recordWithSuppliedVersion = new CustomersRecord();
        recordWithSuppliedVersion.setVersion(7);
        given(recordContext.record()).willReturn(recordWithSuppliedVersion);

        listener.insertStart(recordContext);

        assertThat(recordWithSuppliedVersion.getVersion()).isEqualTo(7);
    }

    /** Verifies that insertion leaves unsupported record types unchanged. */
    @Test
    void ignoresUnsupportedRecords() {
        Record otherRecord = mock(Record.class);
        given(recordContext.record()).willReturn(otherRecord);

        listener.insertStart(recordContext);

        // Does not throw and does nothing with unsupported Record
        assertThat(recordContext.record()).isSameAs(otherRecord);
    }
}
