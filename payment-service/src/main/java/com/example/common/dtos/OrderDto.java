/*** Licensed under MIT License Copyright (c) 2021-2024 Raja Kolli. ***/
package com.example.common.dtos;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class OrderDto implements Serializable {
    private Long orderId;
    private @Positive(message = "CustomerId should be positive") Long customerId;
    private String status;
    private String source;
    private @NotEmpty(message = "Order without items not valid") List<OrderItemDto> items;

    private static List<OrderItemDto> $default$items() {
        return new ArrayList<>();
    }

    public static OrderDtoBuilder builder() {
        return new OrderDtoBuilder();
    }

    public Long getOrderId() {
        return this.orderId;
    }

    public Long getCustomerId() {
        return this.customerId;
    }

    public String getStatus() {
        return this.status;
    }

    public String getSource() {
        return this.source;
    }

    public List<OrderItemDto> getItems() {
        return this.items;
    }

    public void setOrderId(final Long orderId) {
        this.orderId = orderId;
    }

    public void setCustomerId(final Long customerId) {
        this.customerId = customerId;
    }

    public void setStatus(final String status) {
        this.status = status;
    }

    public void setSource(final String source) {
        this.source = source;
    }

    public void setItems(final List<OrderItemDto> items) {
        this.items = items;
    }

    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof OrderDto other)) {
            return false;
        } else {
            if (!other.canEqual(this)) {
                return false;
            } else {
                label71:
                {
                    Object this$orderId = this.getOrderId();
                    Object other$orderId = other.getOrderId();
                    if (this$orderId == null) {
                        if (other$orderId == null) {
                            break label71;
                        }
                    } else if (this$orderId.equals(other$orderId)) {
                        break label71;
                    }

                    return false;
                }

                Object this$customerId = this.getCustomerId();
                Object other$customerId = other.getCustomerId();
                if (this$customerId == null) {
                    if (other$customerId != null) {
                        return false;
                    }
                } else if (!this$customerId.equals(other$customerId)) {
                    return false;
                }

                label57:
                {
                    Object this$status = this.getStatus();
                    Object other$status = other.getStatus();
                    if (this$status == null) {
                        if (other$status == null) {
                            break label57;
                        }
                    } else if (this$status.equals(other$status)) {
                        break label57;
                    }

                    return false;
                }

                Object this$source = this.getSource();
                Object other$source = other.getSource();
                if (this$source == null) {
                    if (other$source != null) {
                        return false;
                    }
                } else if (!this$source.equals(other$source)) {
                    return false;
                }

                Object this$items = this.getItems();
                Object other$items = other.getItems();
                if (this$items == null) {
                    return other$items == null;
                } else return this$items.equals(other$items);
            }
        }
    }

    protected boolean canEqual(final Object other) {
        return other instanceof OrderDto;
    }

    public int hashCode() {
        int result = 1;
        Object $orderId = this.getOrderId();
        result = result * 59 + ($orderId == null ? 43 : $orderId.hashCode());
        Object $customerId = this.getCustomerId();
        result = result * 59 + ($customerId == null ? 43 : $customerId.hashCode());
        Object $status = this.getStatus();
        result = result * 59 + ($status == null ? 43 : $status.hashCode());
        Object $source = this.getSource();
        result = result * 59 + ($source == null ? 43 : $source.hashCode());
        Object $items = this.getItems();
        result = result * 59 + ($items == null ? 43 : $items.hashCode());
        return result;
    }

    public String toString() {
        return "OrderDto(orderId="
                + this.getOrderId()
                + ", customerId="
                + this.getCustomerId()
                + ", status="
                + this.getStatus()
                + ", source="
                + this.getSource()
                + ", items="
                + this.getItems()
                + ")";
    }

    public OrderDto(
            final Long orderId,
            final Long customerId,
            final String status,
            final String source,
            final List<OrderItemDto> items) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.status = status;
        this.source = source;
        this.items = items;
    }

    public OrderDto() {
        this.items = $default$items();
    }

    public static class OrderDtoBuilder {

        private Long orderId;

        private Long customerId;

        private String status;

        private String source;

        private boolean items$set;

        private List<OrderItemDto> items$value;

        OrderDtoBuilder() {}

        public OrderDtoBuilder orderId(final Long orderId) {
            this.orderId = orderId;
            return this;
        }

        public OrderDtoBuilder customerId(final Long customerId) {
            this.customerId = customerId;
            return this;
        }

        public OrderDtoBuilder status(final String status) {
            this.status = status;
            return this;
        }

        public OrderDtoBuilder source(final String source) {
            this.source = source;
            return this;
        }

        public OrderDtoBuilder items(final List<OrderItemDto> items) {
            this.items$value = items;
            this.items$set = true;
            return this;
        }

        public OrderDto build() {
            List<OrderItemDto> items$value = this.items$value;
            if (!this.items$set) {
                items$value = OrderDto.$default$items();
            }

            return new OrderDto(
                    this.orderId, this.customerId, this.status, this.source, items$value);
        }

        public String toString() {
            return "OrderDto.OrderDtoBuilder(orderId="
                    + this.orderId
                    + ", customerId="
                    + this.customerId
                    + ", status="
                    + this.status
                    + ", source="
                    + this.source
                    + ", items$value="
                    + this.items$value
                    + ")";
        }
    }
}
