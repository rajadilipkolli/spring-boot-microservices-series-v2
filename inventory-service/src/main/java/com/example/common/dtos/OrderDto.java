/***
<p>
    Licensed under MIT License Copyright (c) 2021-2024 Raja Kolli.
</p>
***/

package com.example.common.dtos;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

public class OrderDto implements Serializable {

    private Long orderId;

    private long customerId;

    private String status;

    private String source;

    private List<OrderItemDto> items = new ArrayList<>();

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(long customerId) {
        this.customerId = customerId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public List<OrderItemDto> getItems() {
        return items;
    }

    public void setItems(List<OrderItemDto> items) {
        this.items = items;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", OrderDto.class.getSimpleName() + "[", "]")
                .add("orderId=" + orderId)
                .add("customerId=" + customerId)
                .add("status='" + status + "'")
                .add("source='" + source + "'")
                .add("items=" + items)
                .toString();
    }
}
