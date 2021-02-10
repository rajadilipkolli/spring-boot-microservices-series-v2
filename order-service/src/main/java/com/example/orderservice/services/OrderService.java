package com.example.orderservice.services;

import com.example.orderservice.dtos.OrderDto;
import com.example.orderservice.entities.Order;
import com.example.orderservice.mapper.OrderMapper;
import com.example.orderservice.repositories.OrderRepository;
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
        return this.orderMapper.toDtoList(orderRepository.findAllOrders());
    }

    public Optional<OrderDto> findOrderById(Long id) {
        return orderRepository.findOrderById(id).map(this.orderMapper::toDto);
    }

    public OrderDto saveOrder(OrderDto orderDto) {
        Order order = this.orderMapper.toEntity(orderDto);
        return this.orderMapper.toDto(orderRepository.save(order));
    }

    public void deleteOrderById(Long id) {
        orderRepository.deleteById(id);
    }
}
