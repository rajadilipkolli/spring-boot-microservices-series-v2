/* Licensed under Apache-2.0 2021-2022 */
package com.example.orderservice.services;

import com.example.orderservice.dtos.OrderDto;
import com.example.orderservice.entities.Order;
import com.example.orderservice.mapper.OrderMapper;
import com.example.orderservice.repositories.OrderRepository;
import com.example.orderservice.utils.AppConstants;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

    public List<OrderDto> findAllOrders(int pageNo, int pageSize, String sortBy, String sortDir) {
        Sort sort =
                sortDir.equalsIgnoreCase(Sort.Direction.ASC.name())
                        ? Sort.by(sortBy).ascending()
                        : Sort.by(sortBy).descending();

        // create Pageable instance
        Pageable pageable = PageRequest.of(pageNo, pageSize, sort);
        return this.orderMapper.orderToDtoList(orderRepository.findAll(pageable).getContent());
    }

    public Optional<OrderDto> findOrderById(Long id) {
        return orderRepository.findOrderById(id).map(this.orderMapper::toDto);
    }

    @Transactional
    public OrderDto saveOrder(OrderDto orderDto) {
        Order order = this.orderMapper.toEntity(orderDto);
        OrderDto persistedOrderDto = this.orderMapper.toDto(orderRepository.save(order));
        this.template.send(
                AppConstants.ORDERS_TOPIC, persistedOrderDto.getOrderId(), persistedOrderDto);
        return persistedOrderDto;
    }

    public void deleteOrderById(Long id) {
        orderRepository.deleteById(id);
    }
}
