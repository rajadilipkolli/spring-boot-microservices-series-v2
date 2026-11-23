/***
 * <p>
 * Licensed under MIT License Copyright (c) 2021-2026 Raja Kolli.
 * </p>
 ***/

package com.example.common.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledInNativeImage;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

// AspectJProxyFactory generates a CGLIB proxy at runtime.
@DisabledInNativeImage
class IdempotentAspectTest {

    static class SampleService {
        private final AtomicInteger invocationCount = new AtomicInteger(0);

        @Idempotent(key = "#orderId")
        public String processOrder(String orderId, String payload) {
            invocationCount.incrementAndGet();
            return "PROCESSED:" + orderId + ":" + payload;
        }

        @Idempotent(key = "#key.value()")
        public String processWithRecord(IdempotencyKey key, String data) {
            invocationCount.incrementAndGet();
            return "RECORD_OK:" + data;
        }

        @Idempotent
        public String processWithoutKey(String data) {
            invocationCount.incrementAndGet();
            return "DEFAULT_KEY:" + data;
        }

        @Idempotent(key = "#id")
        public String processWithFailure(String id, boolean fail) {
            invocationCount.incrementAndGet();
            if (fail) {
                throw new IllegalStateException("Simulated business error");
            }
            return "OK:" + id;
        }

        @Idempotent
        public String array(Object... args) {
            return String.valueOf(invocationCount.incrementAndGet());
        }

        @Idempotent
        public String array(String arg) {
            return String.valueOf(invocationCount.incrementAndGet());
        }

        @Idempotent
        public String nullable() {
            invocationCount.incrementAndGet();
            return null;
        }

        @Idempotent
        public int unsupported() {
            return invocationCount.incrementAndGet();
        }

        public int getInvocationCount() {
            return invocationCount.get();
        }
    }

    private InMemoryIdempotencyStore store;
    private SampleService proxy;
    private SampleService target;

    @BeforeEach
    void setUp() {
        store = new InMemoryIdempotencyStore();
        IdempotentAspect aspect = new IdempotentAspect(store);

        target = new SampleService();
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.addAspect(aspect);
        proxy = factory.getProxy();
    }

    @Test
    void shouldExecuteMethodAndCacheResultOnFirstCall() {
        String res1 = proxy.processOrder("ord-1", "data");
        assertThat(res1).isEqualTo("PROCESSED:ord-1:data");
        assertThat(target.getInvocationCount()).isEqualTo(1);

        // Second call with same key returns cached result without incrementing invocation count
        String res2 = proxy.processOrder("ord-1", "other-data");
        assertThat(res2).isEqualTo("PROCESSED:ord-1:data");
        assertThat(target.getInvocationCount()).isEqualTo(1);
    }

    @Test
    void shouldSupportIdempotencyKeyRecord() {
        IdempotencyKey key = new IdempotencyKey("rec-99");
        String res1 = proxy.processWithRecord(key, "item");
        assertThat(res1).isEqualTo("RECORD_OK:item");
        assertThat(target.getInvocationCount()).isEqualTo(1);

        // Duplicate call
        String res2 = proxy.processWithRecord(key, "item");
        assertThat(res2).isEqualTo("RECORD_OK:item");
        assertThat(target.getInvocationCount()).isEqualTo(1);
    }

    @Test
    void shouldGenerateDefaultKeyWhenNoSpelProvided() {
        String res1 = proxy.processWithoutKey("hello");
        assertThat(res1).isEqualTo("DEFAULT_KEY:hello");
        assertThat(target.getInvocationCount()).isEqualTo(1);

        // Duplicate call with exact same args
        String res2 = proxy.processWithoutKey("hello");
        assertThat(res2).isEqualTo("DEFAULT_KEY:hello");
        assertThat(target.getInvocationCount()).isEqualTo(1);

        // Call with different args generates different default key
        String res3 = proxy.processWithoutKey("world");
        assertThat(res3).isEqualTo("DEFAULT_KEY:world");
        assertThat(target.getInvocationCount()).isEqualTo(2);
    }

    @Test
    void shouldReleaseLockOnMethodFailureAllowingRetry() {
        assertThatThrownBy(() -> proxy.processWithFailure("err-1", true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Simulated business error");

        assertThat(target.getInvocationCount()).isEqualTo(1);

        // Lock should have been released, so subsequent call can acquire and succeed
        String res = proxy.processWithFailure("err-1", false);
        assertThat(res).isEqualTo("OK:err-1");
        assertThat(target.getInvocationCount()).isEqualTo(2);

        // Subsequent call is now cached
        String cached = proxy.processWithFailure("err-1", false);
        assertThat(cached).isEqualTo("OK:err-1");
        assertThat(target.getInvocationCount()).isEqualTo(2);
    }

    @Test
    void shouldThrowIdempotencyConflictExceptionWhenLockAlreadyHeld() {
        // Pre-acquire lock externally to simulate in-flight execution
        store.acquireLock("in-flight-id", Duration.ofMinutes(5));

        assertThatThrownBy(() -> proxy.processOrder("in-flight-id", "payload"))
                .isInstanceOf(IdempotencyConflictException.class)
                .hasMessageContaining("in-flight-id");

        assertThat(target.getInvocationCount()).isEqualTo(0);
    }

    @Test
    void preservesArgumentBoundariesTypesAndOverloads() {
        assertThat(proxy.array("a, b")).isEqualTo("1");
        assertThat(proxy.array(new Object[] {"a, b"})).isEqualTo("2");
        assertThat(proxy.array("a", "b")).isEqualTo("3");
        assertThat(proxy.array(1)).isEqualTo("4");
        assertThat(proxy.array(new Object[] {"1"})).isEqualTo("5");
        assertThat(proxy.array(new Object[] {"a, b"})).isEqualTo("2");
    }

    @Test
    void replaysNullAndRejectsUnsupportedMethodsBeforeInvocation() {
        assertThat(proxy.nullable()).isNull();
        assertThat(proxy.nullable()).isNull();
        assertThatThrownBy(proxy::unsupported).isInstanceOf(IllegalArgumentException.class);
        assertThat(target.getInvocationCount()).isOne();
    }

    @Test
    void failedCompletionRetriesOnlyTheWriteAndLeavesClaimInPlace() {
        var writes = new AtomicInteger();
        var broken =
                new InMemoryIdempotencyStore() {
                    @Override
                    public boolean markProcessed(
                            String key, String owner, String result, Duration ttl) {
                        writes.incrementAndGet();
                        throw new IllegalStateException("store offline");
                    }
                };
        var factory = new AspectJProxyFactory(target);
        factory.addAspect(new IdempotentAspect(broken));
        SampleService service = factory.getProxy();
        assertThatThrownBy(() -> service.processOrder("k", "p"))
                .isInstanceOf(RuntimeException.class);
        assertThat(writes.get()).isGreaterThan(1);
        assertThatThrownBy(() -> service.processOrder("k", "p"))
                .isInstanceOf(IdempotencyConflictException.class);
        assertThat(target.getInvocationCount()).isOne();
    }
}
