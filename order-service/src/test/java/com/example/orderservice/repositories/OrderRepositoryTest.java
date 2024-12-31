/***
<p>
    Licensed under MIT License Copyright (c) 2023-2024 Raja Kolli.
</p>
***/

package com.example.orderservice.repositories;

import static com.example.orderservice.utils.AppConstants.PROFILE_TEST;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.orderservice.common.PostGreSQLContainer;
import com.example.orderservice.entities.Order;
import com.example.orderservice.util.TestData;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles({PROFILE_TEST})
@Import(PostGreSQLContainer.class)
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=validate"})
class OrderRepositoryTest {

    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderItemRepository orderItemRepository;

    @BeforeEach
    void setUp() {
        this.orderItemRepository.deleteAllInBatch();
        this.orderRepository.deleteAllInBatch();
    }

    @Test
    void findAllOrders() {

        List<Order> orderList = new ArrayList<>();
        for (int i = 0; i <= 15; i++) {
            orderList.add(TestData.getOrder());
        }
        this.orderRepository.saveAll(orderList);

        // create Pageable instance
        Pageable pageable = PageRequest.of(1, 5, Sort.by("id").ascending());

        Page<Long> page = this.orderRepository.findAllOrders(pageable);

        assertThat(page).isNotNull();
        assertThat(page.getTotalElements()).isEqualTo(16);
        assertThat(page.getTotalPages()).isEqualTo(4);
        assertThat(page.getNumber()).isEqualTo(1);
        assertThat(page.isFirst()).isFalse();
        assertThat(page.isLast()).isFalse();
        assertThat(page.hasNext()).isTrue();
        assertThat(page.hasPrevious()).isTrue();
        assertThat(page.getNumberOfElements()).isEqualTo(5);
        assertThat(page.getContent()).isNotEmpty().hasSize(5);
    }
}
