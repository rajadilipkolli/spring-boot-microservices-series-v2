package com.example.orderservice.services;

import com.example.orderservice.dtos.OrderDto;
import com.example.orderservice.dtos.OrderItemDto;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import org.springframework.kafka.config.StreamsBuilderFactoryBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class OrderGeneratorService {

    private static final Random RAND = new Random();
    private final AtomicLong id = new AtomicLong();
    private final KafkaTemplate<Long, OrderDto> template;

    private final StreamsBuilderFactoryBean kafkaStreamsFactory;

    public OrderGeneratorService(
            KafkaTemplate<Long, OrderDto> template, StreamsBuilderFactoryBean kafkaStreamsFactory) {
        this.template = template;
        this.kafkaStreamsFactory = kafkaStreamsFactory;
    }

    @Async
    public void generate() {
        for (int i = 0; i < 10_000; i++) {
            OrderDto o = new OrderDto();
            o.setOrderId(id.incrementAndGet());
            o.setStatus("NEW");
            o.setCustomerId(RAND.nextLong(100) + 1);
            OrderItemDto orderItem = new OrderItemDto();
            int x = RAND.nextInt(5) + 1;
            orderItem.setProductPrice(new BigDecimal(100 * x));
            orderItem.setQuantity(x);
            orderItem.setProductId(RAND.nextLong(100) + 1);
            List<OrderItemDto> oList = new ArrayList<>();
            oList.add(orderItem);
            o.setItems(oList);
            template.send("orders", o.getOrderId(), o);
        }
    }

    public List<OrderDto> getAllOrders() {
        List<OrderDto> orders = new ArrayList<>();
        ReadOnlyKeyValueStore<Long, OrderDto> store =
                kafkaStreamsFactory
                        .getKafkaStreams()
                        .store(
                                StoreQueryParameters.fromNameAndType(
                                        "orders", QueryableStoreTypes.keyValueStore()));
        KeyValueIterator<Long, OrderDto> it = store.all();
        it.forEachRemaining(kv -> orders.add(kv.value));
        return orders;
    }
}
