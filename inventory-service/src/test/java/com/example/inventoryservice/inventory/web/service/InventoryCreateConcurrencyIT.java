/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.inventoryservice.inventory.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.common.dto.ProductDto;
import com.example.inventoryservice.inventory.domain.models.InventoryRequest;
import com.example.inventoryservice.inventory.exception.ProductAlreadyExistsException;
import com.example.inventoryservice.inventory.jooq.tables.records.InventoryRecord;
import com.example.inventoryservice.inventory.shared.SQLContainersConfig;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest(
        properties = {
            "spring.jpa.hibernate.ddl-auto=validate",
            "spring.cloud.config.enabled=false"
        })
@Import(SQLContainersConfig.class)
@AutoConfigureTestDatabase
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class InventoryCreateConcurrencyIT {
    @Autowired private InventoryRepository repository;
    @Autowired private JdbcClient jdbc;
    @Autowired private PlatformTransactionManager transactionManager;

    private InventoryJOOQRepository reads;
    private InventoryService service;
    private ProductManageServiceImpl products;
    private CyclicBarrier barrier;
    private AtomicInteger readCount;

    @BeforeEach
    void setUp() {
        reads = mock(InventoryJOOQRepository.class);
        InventoryRepository writes = repository;
        InventoryMapper mapper = Mappers.getMapper(InventoryMapper.class);
        InventoryRetryHandler retries = new InventoryRetryHandler(transactionManager);
        service = new InventoryService(writes, mapper, reads, retries);
        products = new ProductManageServiceImpl(writes, reads, mapper, retries);
        barrier = new CyclicBarrier(2);
        readCount = new AtomicInteger();
        when(reads.findByProductCode(anyString()))
                .thenAnswer(
                        invocation -> {
                            Optional<InventoryRecord> record = find(invocation.getArgument(0));
                            if (readCount.incrementAndGet() <= 2) {
                                barrier.await(20, TimeUnit.SECONDS);
                            }
                            return record;
                        });
        when(reads.existsByProductCodeAndDeletedFalse(anyString()))
                .thenAnswer(
                        invocation ->
                                find(invocation.getArgument(0))
                                        .filter(record -> !record.getDeleted())
                                        .isPresent());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void concurrentRestCreatesReturnOneDuplicate(boolean flush) throws Exception {
        configureFlush(flush);
        String code = UUID.randomUUID().toString();
        Runnable create = () -> service.saveInventory(new InventoryRequest(code, 12));
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> outcome(create));
            var second = executor.submit(() -> outcome(create));
            assertThat(
                            Arrays.asList(
                                    first.get(30, TimeUnit.SECONDS),
                                    second.get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(null, ProductAlreadyExistsException.class);
        }
        assertSingleRow(code);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void concurrentProductCreatesAreIdempotent(boolean flush) throws Exception {
        configureFlush(flush);
        String code = UUID.randomUUID().toString();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> products.manage(dto(code, false)));
            var second = executor.submit(() -> products.manage(dto(code, false)));
            first.get(30, TimeUnit.SECONDS);
            second.get(30, TimeUnit.SECONDS);
        }
        assertSingleRow(code);
    }

    private void configureFlush(boolean flush) {
        if (flush) {
            // Exercise an immediate SQL failure as well as a deferred commit failure.
            InventoryRepository writes = mock(InventoryRepository.class);
            doAnswer(invocation -> repository.saveAndFlush(invocation.getArgument(0)))
                    .when(writes)
                    .save(any(Inventory.class));
            InventoryMapper mapper = Mappers.getMapper(InventoryMapper.class);
            InventoryRetryHandler retries = new InventoryRetryHandler(transactionManager);
            service = new InventoryService(writes, mapper, reads, retries);
            products = new ProductManageServiceImpl(writes, reads, mapper, retries);
        }
    }

    @Test
    void deletionIntegrityFailuresPropagate() {
        String code = "delete-failure";
        Inventory saved = repository.saveAndFlush(new Inventory().setProductCode(code));
        jdbc.sql(
                        "ALTER TABLE inventory ADD CONSTRAINT test_delete_failure CHECK (product_code <> 'delete-failure' OR NOT deleted)")
                .update();
        try {
            // Do not synchronize this sequential deletion check.
            readCount.set(2);
            when(reads.findByIdAndDeletedFalse(saved.getId())).thenAnswer(invocation -> find(code));
            assertThatThrownBy(() -> service.deleteInventoryById(saved.getId()))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> products.manage(dto(code, true)))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThat(find(code).orElseThrow().getDeleted()).isFalse();
        } finally {
            jdbc.sql("ALTER TABLE inventory DROP CONSTRAINT test_delete_failure").update();
            repository.deleteByProductCode(code);
        }
    }

    private Optional<InventoryRecord> find(String code) {
        return jdbc.sql("SELECT * FROM inventory WHERE product_code = :code")
                .param("code", code)
                .query(
                        (rs, row) -> {
                            InventoryRecord record = new InventoryRecord();
                            record.setId(rs.getLong("id"));
                            record.setProductCode(rs.getString("product_code"));
                            record.setQuantity(rs.getInt("quantity"));
                            record.setReservedItems(rs.getInt("reserved_items"));
                            record.setVersion(rs.getShort("version"));
                            record.setDeleted(rs.getBoolean("deleted"));
                            return record;
                        })
                .optional();
    }

    private void assertSingleRow(String code) {
        assertThat(
                        jdbc.sql("SELECT count(*) FROM inventory WHERE product_code = :code")
                                .param("code", code)
                                .query(Integer.class)
                                .single())
                .isEqualTo(1);
    }

    private static Class<?> outcome(Runnable operation) {
        try {
            operation.run();
            return null;
        } catch (RuntimeException ex) {
            return ex.getClass();
        }
    }

    private static ProductDto dto(String code, boolean deleted) {
        return new ProductDto(code, "Product", "Description", BigDecimal.TEN, deleted);
    }
}
