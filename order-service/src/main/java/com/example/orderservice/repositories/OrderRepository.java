/***
<p>
    Licensed under MIT License Copyright (c) 2021-2026 Raja Kolli.
</p>
***/

package com.example.orderservice.repositories;

import com.example.orderservice.entities.Order;
import com.example.orderservice.entities.OrderStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    // @Query("select o from Order o join fetch o.items where o.id in :orderIds ")
    @EntityGraph(attributePaths = {"items"})
    List<Order> findByIdIn(List<Long> ids);

    @Query("select o from Order o join fetch o.items oi where o.id = :id")
    Optional<Order> findOrderById(@Param("id") Long id);

    @Query(
            value = "select o from Order o join fetch o.items oi where o.customerId = :customerId",
            countQuery = "select count(o) from Order o where o.customerId=:customerId")
    Page<Order> findByCustomerId(@Param("customerId") Long customerId, Pageable pageable);

    @Query("select o.id from Order o where o.customerId = :customerId")
    Page<Long> findAllOrdersByCustomerId(@Param("customerId") Long customerId, Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("update Order o set o.status =:status, o.source =:source where o.id = :id")
    int updateOrderStatusAndSourceById(
            @Param("id") Long orderId,
            @Param("status") OrderStatus status,
            @Param("source") String source);

    @Query("select o.id from Order o")
    Page<Long> findAllOrders(Pageable pageable);

    @EntityGraph(attributePaths = {"items"})
    List<Order> findByStatusAndLastModifiedDateLessThanOrderByIdAsc(
            OrderStatus status, LocalDateTime lastModifiedDate);

    /**
     * Finds matching order IDs with a stable sort, defaulting to ascending ID order when unsorted.
     *
     * @param term pattern fragment matched using SQL LIKE; wildcard characters retain their meaning
     * @param customerId customer to filter by, or null for all customers
     * @param status status to filter by, or null for all statuses
     * @param pageable pagination options, optionally sorted by ID
     * @return a page of distinct matching order IDs
     * @throws IllegalArgumentException if sorting uses a property other than ID or ignores case
     */
    default Page<Long> searchOrdersByKeyword(
            String term, Long customerId, OrderStatus status, Pageable pageable) {
        if (pageable.getSort().stream()
                .anyMatch(order -> !"id".equals(order.getProperty()) || order.isIgnoreCase())) {
            throw new IllegalArgumentException("Keyword search only supports sorting by id");
        }
        if (pageable.getSort().isUnsorted()) {
            Sort sort = Sort.by("id");
            pageable =
                    pageable.isPaged()
                            ? PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort)
                            : Pageable.unpaged(sort);
        }
        return findOrderIdsByKeyword(term, customerId, status, pageable);
    }

    /**
     * Finds distinct order IDs by case-insensitive keyword matching across source, delivery
     * address, and item product codes.
     *
     * @param term pattern fragment matched using SQL LIKE; wildcard characters retain their meaning
     * @param customerId customer to filter by, or null for all customers
     * @param status status to filter by, or null for all statuses
     * @param pageable pagination and sorting options
     * @return a page of matching order IDs
     */
    @Query(
            value =
                    """
                            SELECT DISTINCT o.id FROM Order o LEFT JOIN o.items oi WHERE \
                            (:customerId IS NULL OR o.customerId = :customerId) AND \
                            (:status IS NULL OR o.status = :status) AND \
                            (lower(o.source) LIKE lower(concat('%', :term, '%')) OR \
                            lower(o.deliveryAddress.addressLine1) LIKE lower(concat('%', :term, '%')) OR \
                            lower(o.deliveryAddress.addressLine2) LIKE lower(concat('%', :term, '%')) OR \
                            lower(o.deliveryAddress.city) LIKE lower(concat('%', :term, '%')) OR \
                            lower(o.deliveryAddress.state) LIKE lower(concat('%', :term, '%')) OR \
                            lower(o.deliveryAddress.zipCode) LIKE lower(concat('%', :term, '%')) OR \
                            lower(o.deliveryAddress.country) LIKE lower(concat('%', :term, '%')) OR \
                            lower(oi.productCode) LIKE lower(concat('%', :term, '%')))""",
            countQuery =
                    """
                            SELECT count(DISTINCT o.id) FROM Order o LEFT JOIN o.items oi WHERE \
                            (:customerId IS NULL OR o.customerId = :customerId) AND \
                            (:status IS NULL OR o.status = :status) AND \
                            (lower(o.source) LIKE lower(concat('%', :term, '%')) OR \
                            lower(o.deliveryAddress.addressLine1) LIKE lower(concat('%', :term, '%')) OR \
                            lower(o.deliveryAddress.addressLine2) LIKE lower(concat('%', :term, '%')) OR \
                            lower(o.deliveryAddress.city) LIKE lower(concat('%', :term, '%')) OR \
                            lower(o.deliveryAddress.state) LIKE lower(concat('%', :term, '%')) OR \
                            lower(o.deliveryAddress.zipCode) LIKE lower(concat('%', :term, '%')) OR \
                            lower(o.deliveryAddress.country) LIKE lower(concat('%', :term, '%')) OR \
                            lower(oi.productCode) LIKE lower(concat('%', :term, '%')))""")
    Page<Long> findOrderIdsByKeyword(
            @Param("term") String term,
            @Param("customerId") Long customerId,
            @Param("status") OrderStatus status,
            Pageable pageable);

    /**
     * Finds distinct order IDs using PostgreSQL trigram matching across source, delivery address,
     * and item product codes, ordered by descending maximum similarity.
     *
     * @param term text to compare with searchable fields
     * @param threshold explicit similarity cutoff, applied in addition to the database's trigram
     *     operator; a match from either condition is included
     * @param customerId customer to filter by, or null for all customers
     * @param status status name to filter by, or null for all statuses
     * @param pageable pagination options
     * @return a page of matching order IDs ranked by similarity
     */
    @NativeQuery(
            value =
                    """
                            SELECT o.id FROM orders o LEFT JOIN order_items oi ON o.id = oi.order_id WHERE \
                            (:customerId IS NULL OR o.customer_id = :customerId) AND \
                            (:status IS NULL OR cast(o.status as text) = :status) AND \
                            (o.source % :term OR o.delivery_address_line1 % :term OR o.delivery_address_line2 % :term OR \
                            o.delivery_address_city % :term OR o.delivery_address_state % :term OR o.delivery_address_zip_code % :term OR \
                            o.delivery_address_country % :term OR oi.product_code % :term OR \
                            similarity(COALESCE(o.source, ''), :term) > :threshold OR similarity(COALESCE(o.delivery_address_line1, ''), :term) > :threshold OR \
                            similarity(COALESCE(o.delivery_address_line2, ''), :term) > :threshold OR similarity(COALESCE(o.delivery_address_city, ''), :term) > :threshold OR \
                            similarity(COALESCE(o.delivery_address_state, ''), :term) > :threshold OR similarity(COALESCE(o.delivery_address_zip_code, ''), :term) > :threshold OR \
                            similarity(COALESCE(o.delivery_address_country, ''), :term) > :threshold OR similarity(COALESCE(oi.product_code, ''), :term) > :threshold) \
                            GROUP BY o.id \
                            ORDER BY MAX(greatest(similarity(COALESCE(o.source, ''), :term), similarity(COALESCE(o.delivery_address_line1, ''), :term), \
                            similarity(COALESCE(o.delivery_address_line2, ''), :term), similarity(COALESCE(o.delivery_address_city, ''), :term), \
                            similarity(COALESCE(o.delivery_address_state, ''), :term), similarity(COALESCE(o.delivery_address_zip_code, ''), :term), \
                            similarity(COALESCE(o.delivery_address_country, ''), :term), similarity(COALESCE(oi.product_code, ''), :term))) DESC, o.id""",
            countQuery =
                    """
                            SELECT count(DISTINCT o.id) FROM orders o LEFT JOIN order_items oi ON o.id = oi.order_id WHERE \
                            (:customerId IS NULL OR o.customer_id = :customerId) AND \
                            (:status IS NULL OR cast(o.status as text) = :status) AND \
                            (o.source % :term OR o.delivery_address_line1 % :term OR o.delivery_address_line2 % :term OR \
                            o.delivery_address_city % :term OR o.delivery_address_state % :term OR o.delivery_address_zip_code % :term OR \
                            o.delivery_address_country % :term OR oi.product_code % :term OR \
                            similarity(COALESCE(o.source, ''), :term) > :threshold OR similarity(COALESCE(o.delivery_address_line1, ''), :term) > :threshold OR \
                            similarity(COALESCE(o.delivery_address_line2, ''), :term) > :threshold OR similarity(COALESCE(o.delivery_address_city, ''), :term) > :threshold OR \
                            similarity(COALESCE(o.delivery_address_state, ''), :term) > :threshold OR similarity(COALESCE(o.delivery_address_zip_code, ''), :term) > :threshold OR \
                            similarity(COALESCE(o.delivery_address_country, ''), :term) > :threshold OR similarity(COALESCE(oi.product_code, ''), :term) > :threshold)""")
    Page<Long> searchOrdersBySimilarity(
            @Param("term") String term,
            @Param("threshold") Double threshold,
            @Param("customerId") Long customerId,
            @Param("status") String status,
            Pageable pageable);
}
