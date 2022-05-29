package com.example.paymentservice.repositories;

import com.example.paymentservice.entities.Order;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @EntityGraph(attributePaths = {"items"})
    List<Order> findAll();

    @EntityGraph(attributePaths = {"items"})
    Optional<Order> findById(Long id);
}
