/* Licensed under Apache-2.0 2022 */
package com.example.orderservice.services;

import com.example.common.dtos.OrderDto;
import com.example.orderservice.model.request.OrderItemRequest;
import com.example.orderservice.model.request.OrderRequest;
import com.example.orderservice.utils.AppConstants;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import org.springframework.kafka.config.StreamsBuilderFactoryBean;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class OrderGeneratorService {

    private static final SecureRandom RAND = new SecureRandom();

    private final OrderService orderService;

    private final StreamsBuilderFactoryBean kafkaStreamsFactory;

    public OrderGeneratorService(
            OrderService orderService, StreamsBuilderFactoryBean kafkaStreamsFactory) {
        this.orderService = orderService;
        this.kafkaStreamsFactory = kafkaStreamsFactory;
    }

    @Async
    public void generate() {
        for (int i = 0; i < 10_000; i++) {
            int x = RAND.nextInt(5) + 1;
            OrderItemRequest orderItem =
                    new OrderItemRequest(
                            "Product" + RAND.nextInt(100) + 1, x, new BigDecimal(100 * x));
            int y = RAND.nextInt(5) + 1;
            OrderItemRequest orderItem1 =
                    new OrderItemRequest(
                            "Product" + RAND.nextInt(100) + 1, y, new BigDecimal(100 * y));
            OrderRequest o =
                    new OrderRequest(RAND.nextLong(100) + 1, List.of(orderItem, orderItem1));
            orderService.saveOrder(o);
        }
    }

    public List<OrderDto> getAllOrders(int pageNo, int pageSize) {
        List<OrderDto> orders = new ArrayList<>();
        ReadOnlyKeyValueStore<Long, OrderDto> store =
                Objects.requireNonNull(kafkaStreamsFactory.getKafkaStreams())
                        .store(
                                StoreQueryParameters.fromNameAndType(
                                        AppConstants.ORDERS_TOPIC,
                                        QueryableStoreTypes.keyValueStore()));
        int from = pageNo * pageSize;
        int to = from + pageSize;
        KeyValueIterator<Long, OrderDto> it = store.range((long) (from + 1), (long) to);
        it.forEachRemaining(kv -> orders.add(kv.value));
        return orders;
    }
}
