/* Licensed under Apache-2.0 2022 */
package com.example.paymentservice.services;

import com.example.orderservice.dtos.OrderDto;
import com.example.paymentservice.entities.Order;
import com.example.paymentservice.mapper.OrderMapper;
import com.example.paymentservice.repositories.OrderRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;

    @Autowired
    public OrderService(OrderRepository orderRepository, OrderMapper orderMapper) {
        this.orderRepository = orderRepository;
        this.orderMapper = orderMapper;
    }

    public List<OrderDto> findAllOrders() {
        return this.orderMapper.toDtoList(orderRepository.findAll());
    }

    public Optional<OrderDto> findOrderById(Long id) {
        return orderRepository.findById(id).map(this.orderMapper::toDto);
    }

    public Order saveOrder(Order order) {
        return orderRepository.save(order);
    }
}
