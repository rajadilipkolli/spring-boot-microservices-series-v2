/***
<p>
    Licensed under MIT License Copyright (c) 2023 Raja Kolli.
</p>
***/

package com.example.orderservice.repositories;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.orderservice.common.PostGreSQLContainer;
import com.example.orderservice.entities.Order;
import com.example.orderservice.util.TestData;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@ImportTestcontainers(PostGreSQLContainer.class)
@DataJpaTest(properties = "application.catalogServiceUrl=http://dummy")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OrderRepositoryTest {

    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderItemRepository orderItemRepository;

    @AfterEach
    void tearDown() {
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
        assertThat(page.isFirst()).isEqualTo(false);
        assertThat(page.isLast()).isEqualTo(false);
        assertThat(page.hasNext()).isEqualTo(true);
        assertThat(page.hasPrevious()).isEqualTo(true);
        assertThat(page.getNumberOfElements()).isEqualTo(5);
        assertThat(page.getContent()).isNotEmpty().hasSize(5);
    }
}
