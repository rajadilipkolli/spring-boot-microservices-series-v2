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
import org.apache.kafka.streams.StoreQueryParameters;
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

    public List<OrderDto> getAllOrders(int pageNo, int pageSize) {
        log.info(
                "Fetching all orders from Kafka Store with pageNo :{} and pageSize : {}",
                pageNo,
                pageSize);
        List<OrderDto> orders = new ArrayList<>();
        ReadOnlyKeyValueStore<Long, OrderDto> store =
                Objects.requireNonNull(kafkaStreamsFactory.getKafkaStreams())
                        .store(
                                StoreQueryParameters.fromNameAndType(
                                        AppConstants.ORDERS_STORE,
                                        QueryableStoreTypes.keyValueStore()));

        // Use store.all() to get all entries regardless of key values
        // Then manually handle pagination
        try (KeyValueIterator<Long, OrderDto> it = store.all()) {
            int currentIndex = 0;
            int startIndex = pageNo * pageSize;
            int endIndex = startIndex + pageSize;

            log.info("Store iteration - startIndex: {}, endIndex: {}", startIndex, endIndex);

            while (it.hasNext()) {
                var kv = it.next();
                log.debug(
                        "Found entry at index {}: key={}, value={}",
                        currentIndex,
                        kv.key,
                        kv.value);

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
}
