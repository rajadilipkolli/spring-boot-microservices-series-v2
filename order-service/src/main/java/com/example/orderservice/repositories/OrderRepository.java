/***
<p>
    Licensed under MIT License Copyright (c) 2021-2026 Raja Kolli.
</p>
***/

package com.example.orderservice.repositories;

import com.example.orderservice.entities.Order;
import com.example.orderservice.entities.OrderStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    // Intentionally unfiltered for internal batch hydration and event processing
    @EntityGraph(attributePaths = {"items"})
    List<Order> findByIdIn(List<Long> ids);

    /**
     * Finds active order by ID.
     *
     * @param id order ID
     * @return active order if present and not deleted
     */
    Optional<Order> findByIdAndDeletedFalse(Long id);

    /**
     * Finds an active order with its items loaded, or empty if absent, deleted, or without items.
     * Persistence failures propagate.
     */
    @Query("select o from Order o join fetch o.items oi where o.id = :id and o.deleted = false")
    Optional<Order> findOrderById(@Param("id") Long id);

    /**
     * Returns a page of active orders for the customer with items loaded; orders without items are
     * excluded from the content. The count query counts all active orders for the customer.
     * Persistence failures propagate.
     */
    @Query(
            value =
                    "select o from Order o join fetch o.items oi where o.customerId = :customerId and o.deleted = false",
            countQuery =
                    "select count(o) from Order o where o.customerId = :customerId and o.deleted = false")
    Page<Order> findByCustomerId(@Param("customerId") Long customerId, Pageable pageable);

    /** Returns a page of active order IDs for the customer; persistence failures propagate. */
    @Query("select o.id from Order o where o.customerId = :customerId and o.deleted = false")
    Page<Long> findAllOrdersByCustomerId(@Param("customerId") Long customerId, Pageable pageable);

    /**
     * Updates status and source only while the identified order is NEW. Flushes pending changes
     * before the update and clears the persistence context afterward. Retained unfiltered for
     * processing inflight orders even if soft-deleted.
     *
     * @return the number of updated orders, or zero if the order is absent or no longer NEW
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query(
            "update Order o set o.status =:status, o.source =:source where o.id = :id and o.status = com.example.orderservice.entities.OrderStatus.NEW")
    int updateOrderStatusAndSourceById(
            @Param("id") Long orderId,
            @Param("status") OrderStatus status,
            @Param("source") String source);

    /** Returns a page of active order IDs; persistence failures propagate. */
    @Query("select o.id from Order o where o.deleted = false")
    Page<Long> findAllOrders(Pageable pageable);

    /**
     * Finds orders with the given status and a last modification time strictly before the cutoff,
     * ordered by ID with items loaded. Includes soft-deleted orders for background retries; orders
     * without a last modification time are excluded. Persistence failures propagate.
     *
     * @param lastModifiedDate exclusive cutoff for the last modification time
     */
    @EntityGraph(attributePaths = {"items"})
    List<Order> findByStatusAndLastModifiedDateLessThanOrderByIdAsc(
            OrderStatus status, Instant lastModifiedDate);
}
