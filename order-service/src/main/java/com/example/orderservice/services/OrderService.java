package com.example.orderservice.services;

import com.example.orderservice.dtos.OrderDto;
import com.example.orderservice.entities.Order;
import com.example.orderservice.mapper.OrderMapper;
import com.example.orderservice.repositories.OrderRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;

    private final KafkaTemplate<Long, OrderDto> template;

    public List<OrderDto> findAllOrders() {
        return this.orderMapper.toDtoList(orderRepository.findAllOrders());
    }

    public Optional<OrderDto> findOrderById(Long id) {
        return orderRepository.findOrderById(id).map(this.orderMapper::toDto);
    }

    @Transactional
    public OrderDto saveOrder(OrderDto orderDto) {
        Order order = this.orderMapper.toEntity(orderDto);
        OrderDto persistedOrderDto = this.orderMapper.toDto(orderRepository.save(order));
        this.template.send("orders", persistedOrderDto.getOrderId(), persistedOrderDto);
        return persistedOrderDto;
    }

    public void deleteOrderById(Long id) {
        orderRepository.deleteById(id);
    }
}
