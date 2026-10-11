/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.orderservice.services;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.example.orderservice.exception.OrderStoreUnavailableException;
import com.example.orderservice.model.dtos.OrderDto;
import java.util.List;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.errors.InvalidStateStoreException;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.kafka.config.StreamsBuilderFactoryBean;

class OrderKafkaStreamServiceTest {
    private final StreamsBuilderFactoryBean factory = mock(StreamsBuilderFactoryBean.class);
    private final KafkaStreams streams = mock(KafkaStreams.class);
    private final ReadOnlyKeyValueStore<String, OrderDto> store = mock(ReadOnlyKeyValueStore.class);
    private final OrderKafkaStreamService service = new OrderKafkaStreamService(factory);

    private void runningStore() {
        when(factory.getKafkaStreams()).thenReturn(streams);
        when(streams.state()).thenReturn(KafkaStreams.State.RUNNING);
        when(streams.store(any())).thenReturn(store);
    }

    private void assertUnavailable() {
        assertThatThrownBy(() -> service.getOrderFromStoreById("1"))
                .isInstanceOf(OrderStoreUnavailableException.class);
        assertThatThrownBy(() -> service.getAllOrders(0, 10))
                .isInstanceOf(OrderStoreUnavailableException.class);
    }

    @Test
    void absentStreamsAreUnavailable() {
        assertUnavailable();
    }

    @ParameterizedTest
    @EnumSource(value = KafkaStreams.State.class, names = "RUNNING", mode = EnumSource.Mode.EXCLUDE)
    void nonRunningStreamsAreUnavailable(KafkaStreams.State state) {
        when(factory.getKafkaStreams()).thenReturn(streams);
        when(streams.state()).thenReturn(state);
        assertUnavailable();
    }

    @Test
    void storeAcquisitionFailureIsUnavailable() {
        runningStore();
        when(streams.store(any())).thenThrow(new InvalidStateStoreException("rebalance"));
        assertUnavailable();
    }

    @Test
    void completedQueriesCanReturnEmpty() {
        runningStore();
        KeyValueIterator<String, OrderDto> iterator = mock(KeyValueIterator.class);
        when(store.all()).thenReturn(iterator);
        assertThat(service.getOrderFromStoreById("1")).isEmpty();
        assertThat(service.getAllOrders(0, 10)).isEmpty();
        verify(iterator).close();
    }

    @Test
    void lookupAndIteratorAcquisitionFailuresAreUnavailable() {
        runningStore();
        var cause = new InvalidStateStoreException("rebalance");
        when(store.get("1")).thenThrow(cause);
        when(store.all()).thenThrow(cause);
        assertThatThrownBy(() -> service.getOrderFromStoreById("1"))
                .isInstanceOf(OrderStoreUnavailableException.class)
                .hasCause(cause);
        assertThatThrownBy(() -> service.getAllOrders(0, 10))
                .isInstanceOf(OrderStoreUnavailableException.class)
                .hasCause(cause);
    }

    @Test
    void interruptedIterationDoesNotReturnPartialResults() {
        runningStore();
        KeyValueIterator<String, OrderDto> iterator = mock(KeyValueIterator.class);
        when(store.all()).thenReturn(iterator);
        when(iterator.hasNext())
                .thenReturn(true)
                .thenThrow(new InvalidStateStoreException("rebalance"));
        when(iterator.next())
                .thenReturn(KeyValue.pair("1", new OrderDto(1L, 2L, "NEW", null, List.of())));
        assertThatThrownBy(() -> service.getAllOrders(0, 10))
                .isInstanceOf(OrderStoreUnavailableException.class);
        verify(iterator).close();
    }
}
