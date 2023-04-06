/* Licensed under Apache-2.0 2022 */
package com.example.orderservice.repositories;

import com.example.orderservice.entities.OrderItem;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {}
