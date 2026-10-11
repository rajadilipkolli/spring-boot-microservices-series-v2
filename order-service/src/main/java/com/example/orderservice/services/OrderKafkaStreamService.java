/***
<p>
    Licensed under MIT License Copyright (c) 2022-2026 Raja Kolli.
</p>
***/

package com.example.orderservice.services;

import com.example.orderservice.config.logging.Loggable;
import com.example.orderservice.exception.OrderStoreUnavailableException;
import com.example.orderservice.model.dtos.OrderDto;
import com.example.orderservice.utils.AppConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.errors.InvalidStateStoreException;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.config.StreamsBuilderFactoryBean;
import org.springframework.stereotype.Service;

@Service
@Loggable
public class OrderKafkaStreamService {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private final StreamsBuilderFactoryBean kafkaStreamsFactory;

    public OrderKafkaStreamService(StreamsBuilderFactoryBean kafkaStreamsFactory) {
        this.kafkaStreamsFactory = kafkaStreamsFactory;
    }

    /**
     * Reads a page of orders in the store's iteration order.
     *
     * @param pageNo zero-based page number
     * @param pageSize maximum number of entries to return
     * @return the page's orders, or an empty list if the page contains no entries
     * @throws OrderStoreUnavailableException if Kafka Streams is absent or not running, or the
     *     store becomes unavailable during acquisition or iteration; no partial page is returned
     */
    public List<OrderDto> getAllOrders(int pageNo, int pageSize) {
        log.info(
                "Fetching all orders from Kafka Store with pageNo :{} and pageSize : {}",
                pageNo,
                pageSize);
        List<OrderDto> orders = new ArrayList<>();

        ReadOnlyKeyValueStore<String, OrderDto> store = getReadOnlyKeyValueStore();

        long startIndex = (long) pageNo * pageSize;
        long endIndex = startIndex + pageSize;
        try (KeyValueIterator<String, OrderDto> it = store.all()) {
            long currentIndex = 0;

            log.info("Store iteration - startIndex: {}, endIndex: {}", startIndex, endIndex);

            while (it.hasNext()) {
                var kv = it.next();
                log.debug("Found entry at index {}: key={}", currentIndex, kv.key);

                if (currentIndex >= startIndex && currentIndex < endIndex) {
                    orders.add(kv.value);
                }
                currentIndex++;

                // Early exit if we've collected enough
                if (currentIndex >= endIndex) {
                    break;
                }
            }

            log.info("Returning {} orders out of {} total entries", orders.size(), currentIndex);
        } catch (InvalidStateStoreException ex) {
            log.warn("Orders store became unavailable during iteration: {}", ex.getMessage());
            throw new OrderStoreUnavailableException(ex);
        }

        return orders;
    }

    /**
     * Looks up an order by its store key.
     *
     * @return the stored order, or an empty optional if the key is absent
     * @throws OrderStoreUnavailableException if Kafka Streams is absent or not running, or the
     *     store is unavailable during acquisition or lookup
     */
    public Optional<OrderDto> getOrderFromStoreById(String orderId) {
        log.info("Fetching order from Kafka Store with orderId :{}", orderId);
        try {
            return Optional.ofNullable(getReadOnlyKeyValueStore().get(orderId));
        } catch (InvalidStateStoreException ex) {
            throw new OrderStoreUnavailableException(ex);
        }
    }

    /**
     * Acquires the orders store from the current Kafka Streams instance on each call.
     *
     * @throws OrderStoreUnavailableException if Kafka Streams is absent or not running, or store
     *     acquisition raises an {@link InvalidStateStoreException}
     */
    private ReadOnlyKeyValueStore<String, OrderDto> getReadOnlyKeyValueStore() {
        try {
            KafkaStreams kafkaStreams = kafkaStreamsFactory.getKafkaStreams();
            if (kafkaStreams == null || kafkaStreams.state() != KafkaStreams.State.RUNNING) {
                log.warn(
                        "Kafka Streams is not in RUNNING state (current state: {})",
                        kafkaStreams == null ? "null" : kafkaStreams.state());
                throw new OrderStoreUnavailableException();
            }
            return kafkaStreams.store(
                    StoreQueryParameters.fromNameAndType(
                            AppConstants.ORDERS_STORE, QueryableStoreTypes.keyValueStore()));
        } catch (InvalidStateStoreException ex) {
            log.warn("Orders store not ready: {}", ex.getMessage());
            throw new OrderStoreUnavailableException(ex);
        }
    }
}
