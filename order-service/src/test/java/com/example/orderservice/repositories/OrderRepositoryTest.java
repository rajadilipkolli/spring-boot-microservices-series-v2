/***
<p>
    Licensed under MIT License Copyright (c) 2023-2026 Raja Kolli.
</p>
***/

package com.example.orderservice.repositories;

import static com.example.orderservice.utils.AppConstants.PROFILE_TEST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.orderservice.common.OrderServicePostGreSQLContainer;
import com.example.orderservice.config.AuditConfiguration;
import com.example.orderservice.entities.Order;
import com.example.orderservice.entities.OrderStatus;
import com.example.orderservice.util.TestData;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles({PROFILE_TEST})
@Import({OrderServicePostGreSQLContainer.class, AuditConfiguration.class})
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=validate"})
class OrderRepositoryTest {

    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderSearchRepository orderSearchRepository;
    @Autowired private OrderItemRepository orderItemRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        this.orderItemRepository.deleteAllInBatch();
        this.orderRepository.deleteAllInBatch();
    }

    @Test
    void findAllOrders() {
        // Arrange - Create test data with unique identifiable information
        List<Order> orderList = new ArrayList<>();
        for (int i = 0; i <= 15; i++) {
            Order order = TestData.getOrder();
            // Set unique customer ID for verification later
            order.setCustomerId(100L + i);
            orderList.add(order);
        }
        this.orderRepository.saveAll(orderList);

        // create Pageable instance
        Pageable pageable = PageRequest.of(1, 5, Sort.by("id").ascending());

        // Act - Fetch orders with pagination
        Page<Long> page = this.orderRepository.findAllOrders(pageable);

        // Assert - Verify pagination metadata
        assertThat(page).isNotNull();
        assertThat(page.getTotalElements()).isEqualTo(16);
        assertThat(page.getTotalPages()).isEqualTo(4);
        assertThat(page.getNumber()).isEqualTo(1);
        assertThat(page.isFirst()).isFalse();
        assertThat(page.isLast()).isFalse();
        assertThat(page.hasNext()).isTrue();
        assertThat(page.hasPrevious()).isTrue();
        assertThat(page.getNumberOfElements()).isEqualTo(5);

        // Verify content
        assertThat(page.getContent()).isNotEmpty().hasSize(5).doesNotContainNull();

        // Verify sort order by retrieving all orders and checking IDs
        List<Order> allOrdersSorted = orderRepository.findAll(Sort.by("id").ascending());

        // Expected IDs on page 1 (0-indexed) with pageSize 5 should be elements 5-9
        List<Long> expectedIds =
                allOrdersSorted.stream().skip(5).limit(5).map(Order::getId).toList();

        assertThat(page.getContent()).containsExactlyElementsOf(expectedIds).isSorted();

        // Verify we can fetch the actual orders using the IDs
        List<Order> fetchedOrders = orderRepository.findAllById(page.getContent());
        assertThat(fetchedOrders).hasSize(5);

        // Verify each fetched order matches one of our created orders
        for (Order fetchedOrder : fetchedOrders) {
            assertThat(fetchedOrder.getCustomerId())
                    .isGreaterThanOrEqualTo(100L)
                    .isLessThanOrEqualTo(115L);
        }
    }

    @Test
    void findOrdersByCustomerId() {
        // Arrange - Create multiple orders with specific customer IDs
        Long customerId1 = 1001L;
        Long customerId2 = 1002L;

        // Create 3 orders for customerId1
        for (int i = 0; i < 3; i++) {
            Order order = TestData.getOrder();
            order.setCustomerId(customerId1);
            this.orderRepository.save(order);
        }

        // Create 2 orders for customerId2
        for (int i = 0; i < 2; i++) {
            Order order = TestData.getOrder();
            order.setCustomerId(customerId2);
            this.orderRepository.save(order);
        } // Act & Assert - Verify orders for customerId1
        Page<Order> customer1OrdersPage =
                this.orderRepository.findByCustomerId(customerId1, Pageable.unpaged());
        List<Order> customer1Orders = customer1OrdersPage.getContent();
        assertThat(customer1Orders)
                .isNotNull()
                .hasSize(3)
                .allMatch(order -> order.getCustomerId().equals(customerId1));

        // Verify orders for customerId2
        Page<Order> customer2OrdersPage =
                this.orderRepository.findByCustomerId(customerId2, Pageable.unpaged());
        List<Order> customer2Orders = customer2OrdersPage.getContent();
        assertThat(customer2Orders)
                .isNotNull()
                .hasSize(2)
                .allMatch(order -> order.getCustomerId().equals(customerId2));

        // Verify no orders for non-existent customer
        Page<Order> nonExistentCustomerOrdersPage =
                this.orderRepository.findByCustomerId(9999L, Pageable.unpaged());
        List<Order> nonExistentCustomerOrders = nonExistentCustomerOrdersPage.getContent();
        assertThat(nonExistentCustomerOrders).isEmpty();

        // Verify total order count
        long totalOrders = this.orderRepository.count();
        assertThat(totalOrders).isEqualTo(5);
    }

    @Nested
    class Search {
        /**
         * Verifies keyword matches in city and source fields and an empty result for an unmatched
         * term.
         */
        @Test
        void searchOrdersByKeyword() {
            Order order1 = TestData.getOrder();
            order1.setSource("WEB");
            order1.setDeliveryAddress(
                    new com.example.orderservice.model.Address(
                            order1.getDeliveryAddress().addressLine1(),
                            order1.getDeliveryAddress().addressLine2(),
                            "New York",
                            order1.getDeliveryAddress().state(),
                            order1.getDeliveryAddress().zipCode(),
                            order1.getDeliveryAddress().country()));
            orderRepository.save(order1);

            Order order2 = TestData.getOrder();
            order2.setSource("MOBILE");
            order2.setDeliveryAddress(
                    new com.example.orderservice.model.Address(
                            order2.getDeliveryAddress().addressLine1(),
                            order2.getDeliveryAddress().addressLine2(),
                            "Los Angeles",
                            order2.getDeliveryAddress().state(),
                            order2.getDeliveryAddress().zipCode(),
                            order2.getDeliveryAddress().country()));
            orderRepository.save(order2);

            Page<Long> results =
                    orderSearchRepository.searchOrdersByKeyword(
                            "York", null, null, PageRequest.of(0, 10));
            assertThat(results.getContent()).containsExactly(order1.getId());

            results =
                    orderSearchRepository.searchOrdersByKeyword(
                            "mob", null, null, PageRequest.of(0, 10));
            assertThat(results.getContent()).containsExactly(order2.getId());

            results =
                    orderSearchRepository.searchOrdersByKeyword(
                            "nomatch", null, null, PageRequest.of(0, 10));
            assertThat(results.getContent()).isEmpty();
        }

        /**
         * Verifies default ID ordering for paged and unpaged searches and explicit descending
         * sorting.
         */
        @Test
        void keywordPagesUseIdOrderAndRespectDescendingSort() {
            List<Long> ids =
                    orderRepository
                            .saveAll(
                                    List.of(
                                            TestData.getOrder(),
                                            TestData.getOrder(),
                                            TestData.getOrder()))
                            .stream()
                            .map(Order::getId)
                            .sorted()
                            .toList();

            for (int page = 0; page < ids.size(); page++) {
                Page<Long> results =
                        orderSearchRepository.searchOrdersByKeyword(
                                "Product", null, null, PageRequest.of(page, 1));
                assertThat(results.getContent()).containsExactly(ids.get(page));
                assertThat(results.getTotalElements()).isEqualTo(ids.size());
                Page<Long> descending =
                        orderSearchRepository.searchOrdersByKeyword(
                                "Product",
                                null,
                                null,
                                PageRequest.of(page, 1, Sort.by("id").descending()));
                assertThat(descending.getContent()).containsExactly(ids.get(ids.size() - page - 1));
            }
            assertThat(
                            orderSearchRepository
                                    .searchOrdersByKeyword(
                                            "Product", null, null, Pageable.unpaged())
                                    .getContent())
                    .containsExactlyElementsOf(ids);
        }

        /**
         * Verifies that keyword searches reject unsupported sort properties and case-insensitive
         * sorting.
         */
        @Test
        void keywordSearchRejectsNonIdAndCaseInsensitiveSorts() {
            for (Sort sort :
                    List.of(
                            Sort.by("source"),
                            Sort.by("id", "source"),
                            Sort.by(Sort.Order.asc("id").ignoreCase()))) {
                assertThatThrownBy(
                                () ->
                                        orderSearchRepository.searchOrdersByKeyword(
                                                "Product", null, null, PageRequest.of(0, 10, sort)))
                        .hasMessageContaining("Keyword search only supports sorting by id");
            }
        }

        @ParameterizedTest
        @CsvSource({"0.3, 0.8, 3", "0.3, 1.0, 3", "0.9, 0.3, 3", "0.9, 0.8, 2"})
        void similarityMatchesEitherConditionInContentAndCount(
                double operatorThreshold, double threshold, int expectedCount) {
            // Keep the operator cutoff deterministic and scoped to this test's transaction.
            jdbcTemplate.queryForObject(
                    "SELECT set_config('pg_trgm.similarity_threshold', ?, true)",
                    String.class,
                    Double.toString(operatorThreshold));
            List<Order> orders =
                    orderRepository.saveAll(
                            List.of(
                                    TestData.getOrder().setSource("SomeWeb"),
                                    TestData.getOrder().setSource("SomeWeb"),
                                    TestData.getOrder().setSource("SomeWebSource")));

            // A full page forces the count query; exact matches may appear in either order.
            Page<Long> firstPage =
                    orderSearchRepository.searchOrdersBySimilarity(
                            "SomeWeb", threshold, null, null, PageRequest.of(0, 2));
            assertThat(firstPage.getContent())
                    .containsExactlyInAnyOrder(orders.get(0).getId(), orders.get(1).getId());
            assertThat(firstPage.getTotalElements()).isEqualTo(expectedCount);

            // The partial match qualifies through either cutoff, but not when both exclude it.
            Page<Long> nextPage =
                    orderSearchRepository.searchOrdersBySimilarity(
                            "SomeWeb", threshold, null, null, PageRequest.of(1, 2));
            List<Long> expectedRemainingIds =
                    expectedCount == 3 ? List.of(orders.get(2).getId()) : List.of();
            assertThat(nextPage.getContent()).containsExactlyElementsOf(expectedRemainingIds);
            assertThat(nextPage.getTotalElements()).isEqualTo(expectedCount);

            Page<Long> beyondLastPage =
                    orderSearchRepository.searchOrdersBySimilarity(
                            "SomeWeb", threshold, null, null, PageRequest.of(2, 2));
            assertThat(beyondLastPage.getContent()).isEmpty();
            assertThat(beyondLastPage.getTotalElements()).isEqualTo(expectedCount);
        }

        /** Verifies trigram source matching and an empty result for an unrelated term. */
        @Test
        void searchOrdersBySimilarity() {
            Order order1 = TestData.getOrder();
            order1.setSource("SomeWebSource");
            orderRepository.save(order1);

            Order order2 = TestData.getOrder();
            order2.setSource("OtherSource");
            orderRepository.save(order2);

            Page<Long> results =
                    orderSearchRepository.searchOrdersBySimilarity(
                            "SomeWeb", 0.3, null, null, PageRequest.of(0, 10));
            assertThat(results.getContent()).containsExactly(order1.getId());

            results =
                    orderSearchRepository.searchOrdersBySimilarity(
                            "NoMatchHere", 0.3, null, null, PageRequest.of(0, 10));
            assertThat(results.getContent()).isEmpty();
        }

        @Test
        void
                findByStatusAndLastModifiedDateLessThanOrderByIdAsc_RespectsRetryBoundaryAndStatusFilter() {
            Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
            Instant cutoff = now.minus(5, ChronoUnit.MINUTES);

            // Row 1: NEW, lastModifiedDate 10 minutes ago (with explicit SQL offset +00) -> should
            // be included
            jdbcTemplate.update(
                    """
                    INSERT INTO orders (id, customer_id, status, source, delivery_address_line1, delivery_address_city,
                                        delivery_address_state, delivery_address_zip_code, delivery_address_country,
                                        created_by, created_date, last_modified_date)
                    VALUES (1001, 1, 'NEW', 'WEB', 'Line1', 'City', 'State', '12345', 'USA',
                            'system', '2026-01-01 10:00:00+00', '2026-01-01 10:00:00+00')
                    """);

            // Row 2: NEW, lastModifiedDate 10 minutes ago (with explicit SQL offset +05:30) ->
            // should be included
            jdbcTemplate.update(
                    """
                    INSERT INTO orders (id, customer_id, status, source, delivery_address_line1, delivery_address_city,
                                        delivery_address_state, delivery_address_zip_code, delivery_address_country,
                                        created_by, created_date, last_modified_date)
                    VALUES (1002, 1, 'NEW', 'WEB', 'Line1', 'City', 'State', '12345', 'USA',
                            'system', '2026-01-01 10:00:00+00', '2026-01-01 15:30:00+05:30')
                    """);

            // Row 3: CONFIRMED (non-NEW), lastModifiedDate 10 minutes ago -> excluded by status
            jdbcTemplate.update(
                    """
                    INSERT INTO orders (id, customer_id, status, source, delivery_address_line1, delivery_address_city,
                                        delivery_address_state, delivery_address_zip_code, delivery_address_country,
                                        created_by, created_date, last_modified_date)
                    VALUES (1003, 1, 'CONFIRMED', 'WEB', 'Line1', 'City', 'State', '12345', 'USA',
                            'system', '2026-01-01 10:00:00+00', '2026-01-01 10:00:00+00')
                    """);

            // Row 4: NEW, lastModifiedDate NULL -> excluded by null comparison
            jdbcTemplate.update(
                    """
                    INSERT INTO orders (id, customer_id, status, source, delivery_address_line1, delivery_address_city,
                                        delivery_address_state, delivery_address_zip_code, delivery_address_country,
                                        created_by, created_date, last_modified_date)
                    VALUES (1004, 1, 'NEW', 'WEB', 'Line1', 'City', 'State', '12345', 'USA',
                            'system', '2026-01-01 10:00:00+00', NULL)
                    """);

            // Row 5: NEW, lastModifiedDate 1 minute ago (strictly after cutoff) -> excluded by
            // boundary
            jdbcTemplate.update(
                    """
                    INSERT INTO orders (id, customer_id, status, source, delivery_address_line1, delivery_address_city,
                                        delivery_address_state, delivery_address_zip_code, delivery_address_country,
                                        created_by, created_date, last_modified_date)
                    VALUES (1005, 1, 'NEW', 'WEB', 'Line1', 'City', 'State', '12345', 'USA',
                            'system', '2026-01-01 10:00:00+00', ?)
                    """,
                    java.sql.Timestamp.from(now.minus(1, ChronoUnit.MINUTES)));

            // Also test JPA audited row persistence and epoch comparison
            Order auditedOrder = TestData.getOrder();
            auditedOrder.setStatus(OrderStatus.NEW);
            Order savedOrder = orderRepository.saveAndFlush(auditedOrder);
            assertThat(savedOrder.getCreatedDate()).isNotNull();

            // Read back via JDBC to compare epoch seconds
            long epochSecondsFromDb =
                    jdbcTemplate.queryForObject(
                            "SELECT EXTRACT(EPOCH FROM created_date)::bigint FROM orders WHERE id = ?",
                            Long.class,
                            savedOrder.getId());
            assertThat(epochSecondsFromDb)
                    .isCloseTo(
                            savedOrder.getCreatedDate().getEpochSecond(),
                            org.assertj.core.data.Offset.offset(1L));

            List<Order> result =
                    orderRepository.findByStatusAndLastModifiedDateLessThanOrderByIdAsc(
                            OrderStatus.NEW, cutoff);

            assertThat(result).extracting(Order::getId).containsExactly(1001L, 1002L);
        }
    }
}
