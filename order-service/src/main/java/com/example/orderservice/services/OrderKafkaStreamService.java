/***
<p>
    Licensed under MIT License Copyright (c) 2022-2023 Raja Kolli.
</p>
***/

package com.example.orderservice.services;

import com.example.common.dtos.OrderDto;
import com.example.orderservice.utils.AppConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import org.springframework.kafka.config.StreamsBuilderFactoryBean;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderKafkaStreamService {

    private final StreamsBuilderFactoryBean kafkaStreamsFactory;

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
                                        AppConstants.ORDERS_TOPIC,
                                        QueryableStoreTypes.keyValueStore()));
        long from = (long) pageNo * pageSize;
        long to = from + pageSize;
        try (KeyValueIterator<Long, OrderDto> it = store.range(from + 1, to)) {
            it.forEachRemaining(kv -> orders.add(kv.value));
        }

        return orders;
    }
}
