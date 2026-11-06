package com.example.inventoryservice.inventory.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

class InventoryRetryHandlerTest {
    @Test
    void retriesCompleteOperationWhenCommitConflicts() {
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        TransactionStatus first = mock(TransactionStatus.class);
        TransactionStatus second = mock(TransactionStatus.class);
        when(manager.getTransaction(any())).thenReturn(first, second);
        doThrow(new OptimisticLockingFailureException("commit conflict")).when(manager).commit(first);
        AtomicInteger reads = new AtomicInteger();
        Integer result = new InventoryRetryHandler(manager).executeWithRetry(reads::incrementAndGet);
        assertThat(result).isEqualTo(2);
        verify(manager).commit(second);
        verify(manager, times(2)).getTransaction(argThat(definition ->
                definition.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRES_NEW));
    }
}
