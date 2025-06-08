/***
<p>
    Licensed under MIT License Copyright (c) 2023-2024 Raja Kolli.
</p>
***/

package com.example.inventoryservice.repositories;

import static com.example.inventoryservice.jooq.tables.Inventory.INVENTORY;

import com.example.inventoryservice.entities.Inventory;
import com.example.inventoryservice.jooq.tables.records.InventoryRecord;
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
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class InventoryJOOQRepositoryImpl implements InventoryJOOQRepository {

    private final DSLContext dslContext;

    public InventoryJOOQRepositoryImpl(DSLContext dslContext) {
        this.dslContext = dslContext;
    }

    @Override
    public Optional<Inventory> findById(Long inventoryId) {
        return dslContext
                .selectFrom(INVENTORY)
                .where(INVENTORY.ID.eq(inventoryId))
                .fetchOptionalInto(Inventory.class);
    }

    @Override
    public Page<Inventory> findAll(Pageable pageable) {
        return new PageImpl<>(
                dslContext
                        .select(
                                INVENTORY.ID,
                                INVENTORY.PRODUCT_CODE,
                                INVENTORY.QUANTITY,
                                INVENTORY.RESERVED_ITEMS,
                                INVENTORY.VERSION)
                        .from(INVENTORY)
                        .orderBy(getSortFields(pageable.getSort()))
                        .limit(pageable.getPageSize())
                        .offset(pageable.getOffset())
                        .fetchInto(Inventory.class),
                pageable,
                dslContext.fetchCount(INVENTORY));
    }

    @Override
    public Optional<Inventory> findByProductCode(String productCode) {
        return dslContext
                .select(
                        INVENTORY.ID,
                        INVENTORY.PRODUCT_CODE,
                        INVENTORY.QUANTITY,
                        INVENTORY.RESERVED_ITEMS,
                        INVENTORY.VERSION)
                .from(INVENTORY)
                .where(INVENTORY.PRODUCT_CODE.eq(productCode))
                .fetchOptionalInto(Inventory.class);
    }

    @Override
    public List<Inventory> findByProductCodeIn(List<String> productCodes) {
        return dslContext
                .select(
                        INVENTORY.ID,
                        INVENTORY.PRODUCT_CODE,
                        INVENTORY.QUANTITY,
                        INVENTORY.RESERVED_ITEMS,
                        INVENTORY.VERSION)
                .from(INVENTORY)
                .where(INVENTORY.PRODUCT_CODE.in(productCodes))
                .fetchInto(Inventory.class);
    }

    @Override
    @Transactional
    public int deleteByProductCode(String productCode) {
        return dslContext
                .deleteFrom(INVENTORY)
                .where(INVENTORY.PRODUCT_CODE.eq(productCode))
                .execute();
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
            Field tableField =
                    INVENTORY.getClass().getField(sortFieldName.toUpperCase(Locale.ROOT));
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
