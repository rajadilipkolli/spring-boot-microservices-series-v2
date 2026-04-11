/***
<p>
    Licensed under MIT License Copyright (c) 2022-2024 Raja Kolli.
</p>
***/

package com.example.orderservice.services;

import com.example.common.dtos.OrderDto;
import com.example.orderservice.config.logging.Loggable;
import com.example.orderservice.utils.AppConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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

    private ReadOnlyKeyValueStore<Long, OrderDto> store = null;

    public OrderKafkaStreamService(StreamsBuilderFactoryBean kafkaStreamsFactory) {
        this.kafkaStreamsFactory = kafkaStreamsFactory;
    }

    public List<OrderDto> getAllOrders(int pageNo, int pageSize) {
        log.info(
                "Fetching all orders from Kafka Store with pageNo :{} and pageSize : {}",
                pageNo,
                pageSize);
        List<OrderDto> orders = new ArrayList<>();

        long startIndex = (long) pageNo * pageSize;
        long endIndex = startIndex + pageSize;
        try (KeyValueIterator<Long, OrderDto> it = getReadOnlyKeyValueStore().all()) {
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
        }

        return orders;
    }

    public Optional<OrderDto> getOrderFromStoreById(long orderId) {
        log.info("Fetching order from Kafka Store with orderId :{}", orderId);
        return Optional.ofNullable(getReadOnlyKeyValueStore().get(orderId));
    }

    private ReadOnlyKeyValueStore<Long, OrderDto> getReadOnlyKeyValueStore() {
        if (store == null) {
            try {
                store =
                        Objects.requireNonNull(kafkaStreamsFactory.getKafkaStreams())
                                .store(
                                        StoreQueryParameters.fromNameAndType(
                                                AppConstants.ORDERS_STORE,
                                                QueryableStoreTypes.keyValueStore()));
            } catch (InvalidStateStoreException ex) {
                throw new IllegalStateException("Orders store not ready", ex);
            }
        }
        return store;
    }
}
