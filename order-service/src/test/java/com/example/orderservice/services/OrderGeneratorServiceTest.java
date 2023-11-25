/***
<p>
    Licensed under MIT License Copyright (c) 2023 Raja Kolli.
</p>
***/

package com.example.orderservice.services;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.example.orderservice.model.request.OrderRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.config.StreamsBuilderFactoryBean;

@ExtendWith(MockitoExtension.class)
class OrderGeneratorServiceTest {

    @Mock private OrderService orderService;

    @Mock private StreamsBuilderFactoryBean kafkaStreamsFactory;

    @InjectMocks private OrderGeneratorService orderGeneratorService;

    @Test
    void generate() {
        // Arrange
        int expectedOrderCount = 10_000;

        // Act
        orderGeneratorService.generate();

        // Assert
        verify(orderService, times(expectedOrderCount)).saveOrder(any(OrderRequest.class));
    }
}
