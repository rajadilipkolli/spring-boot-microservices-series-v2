/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.inventoryservice.inventory.web.service;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ProcessedOrderEventRepository
        extends JpaRepository<ProcessedOrderEvent, ProcessedOrderEventId> {

    /**
     * Atomically claims an event for an order using ON CONFLICT DO NOTHING.
     *
     * @param orderId unique order identifier
     * @param eventType event category marker
     * @param processedAt timestamp of processing
     * @return 1 if claimed successfully, 0 if already claimed
     */
    @Modifying
    @Query(
            value =
                    """
            INSERT INTO processed_order_events (order_id, event_type, processed_at)
            VALUES (:orderId, :eventType, :processedAt)
            ON CONFLICT (order_id, event_type) DO NOTHING
            """,
            nativeQuery = true)
    int claim(
            @Param("orderId") Long orderId,
            @Param("eventType") String eventType,
            @Param("processedAt") Instant processedAt);

    /**
     * Updates the reservation outcome status for a claimed event.
     *
     * @param orderId unique order identifier
     * @param eventType event category marker
     * @param resultStatus outcome status (e.g. ACCEPT, REJECT)
     * @return number of affected rows
     */
    @Modifying
    @Query(
            value =
                    """
            UPDATE processed_order_events
            SET result_status = :resultStatus
            WHERE order_id = :orderId AND event_type = :eventType
            """,
            nativeQuery = true)
    int updateResultStatus(
            @Param("orderId") Long orderId,
            @Param("eventType") String eventType,
            @Param("resultStatus") String resultStatus);

    /**
     * Reads the stored reservation outcome status for an order's RESERVE event.
     *
     * @param orderId unique order identifier
     * @return the result status if present
     */
    @Query(
            value =
                    """
            SELECT result_status
            FROM processed_order_events
            WHERE order_id = :orderId AND event_type = 'RESERVE'
            """,
            nativeQuery = true)
    Optional<String> findResultStatusByOrderIdAndReserveEventType(@Param("orderId") Long orderId);
}
