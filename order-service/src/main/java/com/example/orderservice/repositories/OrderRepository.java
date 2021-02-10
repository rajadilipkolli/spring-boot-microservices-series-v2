package com.example.orderservice.repositories;

import com.example.orderservice.entities.Order;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @Query("select o from Order o join fetch o.items oi ")
    List<Order> findAllOrders();

    @Query("select o from Order o join fetch o.items oi where o.id = :id")
    Optional<Order> findOrderById(@Param("id") Long id);
}
