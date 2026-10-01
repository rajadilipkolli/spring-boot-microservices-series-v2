/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.orderservice.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.orderservice.entities.Order;
import com.example.orderservice.mapper.OrderMapper;
import com.example.orderservice.model.response.OrderResponse;
import com.example.orderservice.model.response.PagedResult;
import com.example.orderservice.repositories.OrderRepository;
import java.util.List;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderMapper orderMapper;
    @InjectMocks private OrderService orderService;

    /**
     * Verifies that search responses preserve ID page order and metadata when orders load in a
     * different order.
     *
     * @param mode search strategy to exercise
     */
    @ParameterizedTest
    @ValueSource(strings = {"keyword", "similarity"})
    void searchPreservesIdPageOrderAndMetadata(String mode) {
        PageRequest pageable = PageRequest.of(1, 3);
        List<Long> ids = List.of(30L, 10L, 20L);
        Page<Long> page = new PageImpl<>(ids, pageable, 8);
        if ("similarity".equals(mode)) {
            when(orderRepository.searchOrdersBySimilarity("web", 0.3, null, null, pageable))
                    .thenReturn(page);
        } else {
            when(orderRepository.searchOrdersByKeyword("web", null, null, pageable))
                    .thenReturn(page);
        }
        List<Order> loadedOrders =
                List.of(new Order().setId(10L), new Order().setId(20L), new Order().setId(30L));
        when(orderRepository.findByIdIn(ids)).thenReturn(loadedOrders);
        for (Order order : loadedOrders) {
            when(orderMapper.toResponse(order))
                    .thenReturn(OrderResponse.emptyResponse(order.getId()));
        }
        PagedResult<OrderResponse> result =
                orderService.searchOrders("web", mode, null, null, null, pageable);

        assertThat(result.data()).extracting(OrderResponse::orderId).containsExactly(30L, 10L, 20L);
        assertThat(result.totalElements()).isEqualTo(8);
        assertThat(result.pageNumber()).isEqualTo(2);
        assertThat(result.totalPages()).isEqualTo(3);
        assertThat(result.isFirst()).isFalse();
        assertThat(result.isLast()).isFalse();
        assertThat(result.hasNext()).isTrue();
        assertThat(result.hasPrevious()).isTrue();
    }
}
