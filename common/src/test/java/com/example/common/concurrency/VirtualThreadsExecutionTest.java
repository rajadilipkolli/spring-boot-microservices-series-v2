package com.example.common.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class VirtualThreadsExecutionTest {

    @Test
    void verifiesTaskRunsOnVirtualThread() throws ExecutionException, InterruptedException {
        AtomicBoolean isVirtualThread = new AtomicBoolean(false);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<?> future =
                    executor.submit(
                            () -> {
                                isVirtualThread.set(Thread.currentThread().isVirtual());
                            });
            future.get();
        }
        assertThat(isVirtualThread.get()).isTrue();
    }
}
