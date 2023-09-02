/***
<p>
    Licensed under MIT License Copyright (c) 2021-2023 Raja Kolli.
</p>
***/

package com.example.orderservice.repositories;

import com.example.orderservice.entities.Order;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    // @Query("select o from Order o join fetch o.items where o.id in :orderIds ")
    @EntityGraph(attributePaths = {"items"})
    List<Order> findByIdIn(List<Long> ids);

    @Query("select o from Order o join fetch o.items oi where o.id = :id")
    Optional<Order> findOrderById(@Param("id") Long id);

    @Modifying
    @Transactional
    @Query("update Order o set o.status =:status, o.source =:source where o.id = :id")
    int updateOrderStatusAndSourceById(
            @Param("id") Long orderId,
            @Param("status") String status,
            @Param("source") String source);
}
