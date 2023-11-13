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
import com.example.orderservice.model.response.OrderItemResponse;
import com.example.orderservice.model.response.OrderResponse;
import java.math.RoundingMode;
import org.mapstruct.AfterMapping;
import org.mapstruct.DecoratedWith;
import org.mapstruct.InheritConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValueCheckStrategy;

@Mapper(
        componentModel = "spring",
        nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS,
        imports = RoundingMode.class)
@DecoratedWith(OrderMapperDecorator.class)
public interface OrderMapper {

    @Mapping(source = "id", target = "orderId")
    OrderDto toDto(Order order);

    @Mapping(target = "itemId", source = "id")
    @Mapping(target = "productId", source = "productCode")
    OrderItemDto orderItemToOrderItemDto(OrderItem orderItem);

    @Mapping(target = "version", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "source", ignore = true)
    @Mapping(target = "items", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdDate", ignore = true)
    @Mapping(target = "lastModifiedBy", ignore = true)
    @Mapping(target = "lastModifiedDate", ignore = true)
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

    @InheritConfiguration
    void updateOrderFromOrderRequest(OrderRequest orderRequest, @MappingTarget Order order);

    @Mapping(
            target = "totalPrice",
            expression =
                    "java(items.stream().map(OrderItemResponse::price).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP))")
    @Mapping(source = "id", target = "orderId")
    OrderResponse toResponse(Order order);

    @Mapping(
            target = "price",
            expression =
                    "java(productPrice.multiply(new BigDecimal(quantity)).setScale(2, RoundingMode.HALF_UP))")
    @Mapping(target = "itemId", source = "id")
    @Mapping(target = "productId", source = "productCode")
    OrderItemResponse orderItemToOrderItemResponse(OrderItem orderItem);
}
