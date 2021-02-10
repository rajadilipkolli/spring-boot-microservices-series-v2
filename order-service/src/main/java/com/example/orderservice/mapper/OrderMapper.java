package com.example.orderservice.mapper;

import com.example.orderservice.dtos.OrderDto;
import com.example.orderservice.dtos.OrderItemDto;
import com.example.orderservice.entities.Order;
import com.example.orderservice.entities.OrderItem;
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
        List<OrderItem> list = orderItemDtoListToOrderItemList(orderDto.getItems());
        if (list != null) {
            for (OrderItem orderItem : list) {
                order.addOrderItem(orderItem);
            }
        }

        return order;
    }
}
