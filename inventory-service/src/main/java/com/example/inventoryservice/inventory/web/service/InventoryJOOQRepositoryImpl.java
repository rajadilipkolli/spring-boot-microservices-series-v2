/***
<p>
    Licensed under MIT License Copyright (c) 2023-2026 Raja Kolli.
</p>
***/

package com.example.inventoryservice.inventory.web.service;

import static com.example.inventoryservice.inventory.jooq.tables.Inventory.INVENTORY;

import com.example.inventoryservice.inventory.jooq.tables.records.InventoryRecord;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.SortField;
import org.jooq.TableField;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
class InventoryJOOQRepositoryImpl implements InventoryJOOQRepository {

    private final DSLContext dslContext;

    public InventoryJOOQRepositoryImpl(DSLContext dslContext) {
        this.dslContext = dslContext;
    }

    /**
     * Returns inventory by ID, including soft-deleted records, or empty if absent. Database
     * failures propagate.
     */
    @Override
    public Optional<InventoryRecord> findById(Long inventoryId) {
        return dslContext.selectFrom(INVENTORY).where(INVENTORY.ID.eq(inventoryId)).fetchOptional();
    }

    /**
     * Returns active inventory by ID, or empty if absent or deleted; database failures propagate.
     */
    @Override
    public Optional<InventoryRecord> findByIdAndDeletedFalse(Long inventoryId) {
        return dslContext
                .selectFrom(INVENTORY)
                .where(INVENTORY.ID.eq(inventoryId).and(INVENTORY.DELETED.isFalse()))
                .fetchOptional();
    }

    /** Returns whether active inventory exists for the ID; database failures propagate. */
    @Override
    public boolean existsByIdAndDeletedFalse(Long inventoryId) {
        return dslContext.fetchExists(
                INVENTORY, INVENTORY.ID.eq(inventoryId).and(INVENTORY.DELETED.isFalse()));
    }

    /**
     * Returns the requested sorted page and total count of active inventory. Database failures
     * propagate.
     *
     * @throws InvalidDataAccessApiUsageException if a sort property has no accessible table field
     */
    @Override
    public Page<InventoryRecord> findAllAndDeletedFalse(Pageable pageable) {
        return new PageImpl<>(
                dslContext
                        .selectFrom(INVENTORY)
                        .where(INVENTORY.DELETED.isFalse())
                        .orderBy(getSortFields(pageable.getSort()))
                        .limit(pageable.getPageSize())
                        .offset(pageable.getOffset())
                        .fetch(),
                pageable,
                dslContext.fetchCount(INVENTORY, INVENTORY.DELETED.isFalse()));
    }

    /**
     * Returns active inventory by exact product code, or empty if absent or deleted; database
     * failures propagate.
     */
    @Override
    public Optional<InventoryRecord> findByProductCodeAndDeletedFalse(String productCode) {
        return dslContext
                .selectFrom(INVENTORY)
                .where(INVENTORY.PRODUCT_CODE.eq(productCode).and(INVENTORY.DELETED.isFalse()))
                .fetchOptional();
    }

    /**
     * Returns the requested sorted page of active inventory matching the supplied codes, with a
     * total count of active matches. Database failures propagate.
     *
     * @throws InvalidDataAccessApiUsageException if a sort property has no accessible table field
     */
    @Override
    public Page<InventoryRecord> findByProductCodeInAndDeletedFalse(
            List<String> productCodes, Pageable pageable) {
        return new PageImpl<>(
                dslContext
                        .selectFrom(INVENTORY)
                        .where(
                                INVENTORY
                                        .PRODUCT_CODE
                                        .in(productCodes)
                                        .and(INVENTORY.DELETED.isFalse()))
                        .orderBy(getSortFields(pageable.getSort()))
                        .limit(pageable.getPageSize())
                        .offset(pageable.getOffset())
                        .fetch(),
                pageable,
                dslContext.fetchCount(
                        INVENTORY,
                        INVENTORY.PRODUCT_CODE.in(productCodes).and(INVENTORY.DELETED.isFalse())));
    }

    /**
     * Returns whether active inventory exists for the exact product code; database failures
     * propagate.
     */
    @Override
    public boolean existsByProductCodeAndDeletedFalse(String productCode) {
        return dslContext.fetchExists(
                INVENTORY, INVENTORY.PRODUCT_CODE.eq(productCode).and(INVENTORY.DELETED.isFalse()));
    }

    private Collection<SortField<?>> getSortFields(Sort sortSpecification) {
        Collection<SortField<?>> querySortFields = new ArrayList<>();

        if (sortSpecification == null) {
            return querySortFields;
        }

        for (Sort.Order specifiedField : sortSpecification) {
            String sortFieldName = specifiedField.getProperty();
            Sort.Direction sortDirection = specifiedField.getDirection();

            TableField<InventoryRecord, Object> tableField = getTableField(sortFieldName);
            SortField<?> querySortField = convertTableFieldToSortField(tableField, sortDirection);
            querySortFields.add(querySortField);
        }

        return querySortFields;
    }

    private TableField<InventoryRecord, Object> getTableField(String sortFieldName) {
        TableField<InventoryRecord, Object> sortField;
        try {
            String jooqFieldName =
                    sortFieldName.replaceAll("([a-z])([A-Z]+)", "$1_$2").toUpperCase(Locale.ROOT);
            Field tableField = INVENTORY.getClass().getField(jooqFieldName);
            sortField = (TableField<InventoryRecord, Object>) tableField.get(INVENTORY);
        } catch (NoSuchFieldException | IllegalAccessException ex) {
            String errorMessage = "Could not find table field: %s".formatted(sortFieldName);
            throw new InvalidDataAccessApiUsageException(errorMessage, ex);
        }
        return sortField;
    }

    private SortField<?> convertTableFieldToSortField(
            TableField<InventoryRecord, Object> tableField, Sort.Direction sortDirection) {
        if (sortDirection == Sort.Direction.ASC) {
            return tableField.asc();
        } else {
            return tableField.desc();
        }
    }
}
