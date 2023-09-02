/***
<p>
    Licensed under MIT License Copyright (c) 2021-2023 Raja Kolli.
</p>
***/

package com.example.orderservice.mapper;

import com.example.common.dtos.OrderDto;
import com.example.common.dtos.OrderItemDto;
import com.example.orderservice.entities.Order;
import com.example.orderservice.entities.OrderItem;
import com.example.orderservice.model.request.OrderItemRequest;
import com.example.orderservice.model.request.OrderRequest;
import java.util.function.Consumer;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValueCheckStrategy;

@Mapper(componentModel = "spring", nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS)
public interface OrderMapper {

    @Mapping(source = "id", target = "orderId")
    OrderDto toDto(Order order);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "items", ignore = true)
    Order toEntity(OrderDto orderDto);

    @Mapping(target = "itemId", source = "id")
    @Mapping(target = "productId", source = "productCode")
    OrderItemDto orderItemToOrderItemDto(OrderItem orderItem);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "order", ignore = true)
    @Mapping(target = "productCode", source = "productId")
    OrderItem orderItemDtoToOrderItem(OrderItemDto orderItemDto);

    @AfterMapping
    default void addOrderItemToOrderEntity(OrderDto orderDTO, @MappingTarget Order order) {
        Consumer<OrderItemDto> addOrderItemToOrder =
                orderItemDTO -> order.addOrderItem(orderItemDtoToOrderItem(orderItemDTO));
        orderDTO.getItems().forEach(addOrderItemToOrder);
    }

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "items", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "source", ignore = true)
    Order orderRequestToEntity(OrderRequest orderRequest);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "order", ignore = true)
    OrderItem orderItemRequestToOrderItem(OrderItemRequest orderItemRequest);

    @AfterMapping
    default void addOrderItemRequestToOrderEntity(
            OrderRequest orderRequest, @MappingTarget Order order) {
        orderRequest
                .items()
                .forEach(
                        orderItemRequest ->
                                order.addOrderItem(orderItemRequestToOrderItem(orderItemRequest)));
    }

    @Mapping(target = "status", ignore = true)
    @Mapping(target = "source", ignore = true)
    @Mapping(target = "id", ignore = true)
    void updateOrderFromOrderRequest(OrderRequest orderRequest, @MappingTarget Order order);
}
