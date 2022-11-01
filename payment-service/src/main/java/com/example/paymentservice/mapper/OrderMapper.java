/* Licensed under Apache-2.0 2021-2022 */
package com.example.paymentservice.mapper;

import com.example.orderservice.dtos.OrderDto;
import com.example.orderservice.dtos.OrderItemDto;
import com.example.paymentservice.entities.Order;
import com.example.paymentservice.entities.OrderItem;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValueCheckStrategy;

@Mapper(componentModel = "spring", nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS)
public interface OrderMapper {

    @Mapping(source = "id", target = "orderId")
    OrderDto toDto(Order order);

    @Mapping(target = "itemId", source = "id")
    OrderItemDto orderItemToOrderItemDto(OrderItem orderItem);

    List<OrderDto> toDtoList(List<Order> orderList);

    List<OrderItem> orderItemDtoListToOrderItemList(List<OrderItemDto> items);

    default Order toEntity(OrderDto orderDto) {
        if (orderDto == null) {
            return null;
        }

        Order order = new Order();
        order.setCustomerEmail(orderDto.getCustomerEmail());

        if (orderDto.getCustomerAddress() != null) {
            order.setCustomerAddress(orderDto.getCustomerAddress());
        }
        if (orderDto.getStatus() != null) {
            order.setStatus(orderDto.getStatus());
        }
        if (orderDto.getSource() != null) {
            order.setSource(orderDto.getSource());
        }
        order.setCustomerId(orderDto.getCustomerId());
        List<OrderItem> list = orderItemDtoListToOrderItemList(orderDto.getItems());
        if (list != null) {
            for (OrderItem orderItem : list) {
                order.addOrderItem(orderItem);
            }
        }

        return order;
    }
}
