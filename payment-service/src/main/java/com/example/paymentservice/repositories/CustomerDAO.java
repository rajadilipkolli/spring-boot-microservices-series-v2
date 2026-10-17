/*** Licensed under MIT License Copyright (c) 2026 Raja Kolli. ***/
package com.example.paymentservice.repositories;

import static com.example.paymentservice.jooq.Tables.CUSTOMERS;

import ch.martinelli.oss.jooqspring.JooqDAO;
import com.example.paymentservice.jooq.tables.Customers;
import com.example.paymentservice.jooq.tables.LedgerEntries;
import com.example.paymentservice.jooq.tables.OutboxEvents;
import com.example.paymentservice.jooq.tables.PaymentOperations;
import com.example.paymentservice.jooq.tables.records.CustomersRecord;
import java.math.BigDecimal;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class CustomerDAO extends JooqDAO<Customers, CustomersRecord, Long> {

    /**
     * Constructs a new JooqRepository with the specified DSLContext and table.
     *
     * @param dslContext the DSLContext to be used for database operations
     */
    public CustomerDAO(DSLContext dslContext) {
        super(dslContext, CUSTOMERS);
    }

    /**
     * Finds a customer by name.
     *
     * @return the matching customer, or empty if no name matches
     * @throws org.springframework.dao.DataAccessException if the database query fails
     */
    public Optional<CustomersRecord> findByName(String name) {
        return dslContext.selectFrom(table).where(CUSTOMERS.NAME.eq(name)).fetchOptional();
    }

    /**
     * Finds a customer by email.
     *
     * @return the matching customer, or empty if no email matches
     * @throws org.springframework.dao.DataAccessException if the database query fails
     */
    public Optional<CustomersRecord> findByEmail(String normalizedEmail) {
        return dslContext
                .selectFrom(table)
                .where(CUSTOMERS.EMAIL.eq(normalizedEmail))
                .fetchOptional();
    }

    /**
     * Checks if a customer exists by email.
     *
     * @return true if a customer with the given email exists, false otherwise
     * @throws org.springframework.dao.DataAccessException if the database query fails
     */
    public boolean existsByEmail(String email) {
        return dslContext.fetchExists(table, CUSTOMERS.EMAIL.eq(email));
    }

    @Transactional
    public int deleteAll() {
        dslContext.deleteFrom(LedgerEntries.LEDGER_ENTRIES).execute();
        dslContext.deleteFrom(PaymentOperations.PAYMENT_OPERATIONS).execute();
        dslContext.deleteFrom(OutboxEvents.OUTBOX_EVENTS).execute();
        return dslContext.deleteFrom(table).execute();
    }

    @Transactional
    public boolean reserveFunds(Long customerId, BigDecimal amount) {
        int updated =
                dslContext
                        .update(Customers.CUSTOMERS)
                        .set(
                                Customers.CUSTOMERS.AMOUNT_AVAILABLE,
                                Customers.CUSTOMERS.AMOUNT_AVAILABLE.subtract(amount))
                        .set(
                                Customers.CUSTOMERS.AMOUNT_RESERVED,
                                Customers.CUSTOMERS.AMOUNT_RESERVED.add(amount))
                        .set(Customers.CUSTOMERS.VERSION, Customers.CUSTOMERS.VERSION.plus(1))
                        .where(Customers.CUSTOMERS.ID.eq(customerId))
                        .and(Customers.CUSTOMERS.AMOUNT_AVAILABLE.ge(amount))
                        .execute();
        return updated > 0;
    }

    @Transactional
    public boolean captureFunds(Long customerId, BigDecimal amount) {
        int updated =
                dslContext
                        .update(Customers.CUSTOMERS)
                        .set(
                                Customers.CUSTOMERS.AMOUNT_RESERVED,
                                Customers.CUSTOMERS.AMOUNT_RESERVED.subtract(amount))
                        .set(Customers.CUSTOMERS.VERSION, Customers.CUSTOMERS.VERSION.plus(1))
                        .where(Customers.CUSTOMERS.ID.eq(customerId))
                        .and(Customers.CUSTOMERS.AMOUNT_RESERVED.ge(amount))
                        .execute();
        return updated > 0;
    }

    @Transactional
    public boolean releaseFunds(Long customerId, BigDecimal amount) {
        int updated =
                dslContext
                        .update(Customers.CUSTOMERS)
                        .set(
                                Customers.CUSTOMERS.AMOUNT_RESERVED,
                                Customers.CUSTOMERS.AMOUNT_RESERVED.subtract(amount))
                        .set(
                                Customers.CUSTOMERS.AMOUNT_AVAILABLE,
                                Customers.CUSTOMERS.AMOUNT_AVAILABLE.add(amount))
                        .set(Customers.CUSTOMERS.VERSION, Customers.CUSTOMERS.VERSION.plus(1))
                        .where(Customers.CUSTOMERS.ID.eq(customerId))
                        .and(Customers.CUSTOMERS.AMOUNT_RESERVED.ge(amount))
                        .execute();
        return updated > 0;
    }
}
