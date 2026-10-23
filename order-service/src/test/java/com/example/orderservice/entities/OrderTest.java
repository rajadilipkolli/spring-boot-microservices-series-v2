/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.orderservice.entities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class OrderTest {

    @Nested
    @DisplayName("Lifecycle State Transitions and Guards")
    class LifecycleTransitions {

        @Test
        void confirm_WhenStatusIsNew_ShouldSetStatusConfirmedAndClearSource() {
            Order order = new Order().setStatus(OrderStatus.NEW).setSource("INITIAL");

            order.confirm();

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
            assertThat(order.getSource()).isNull();
        }

        @ParameterizedTest
        @EnumSource(
                value = OrderStatus.class,
                names = {"CONFIRMED", "ROLLBACK", "REJECTED", "FAILED", "ACCEPT"})
        void confirm_WhenStatusIsNotNew_ShouldThrowIllegalStateException(OrderStatus status) {
            Order order = new Order().setStatus(status);

            assertThatThrownBy(order::confirm)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot confirm order in state: " + status);
        }

        @Test
        void rollback_WhenStatusIsNew_ShouldSetStatusRollbackAndSetSource() {
            Order order = new Order().setStatus(OrderStatus.NEW);

            order.rollback("PAYMENT");

            assertThat(order.getStatus()).isEqualTo(OrderStatus.ROLLBACK);
            assertThat(order.getSource()).isEqualTo("PAYMENT");
        }

        @ParameterizedTest
        @EnumSource(
                value = OrderStatus.class,
                names = {"CONFIRMED", "ROLLBACK", "REJECTED", "FAILED", "ACCEPT"})
        void rollback_WhenStatusIsNotNew_ShouldThrowIllegalStateException(OrderStatus status) {
            Order order = new Order().setStatus(status);

            assertThatThrownBy(() -> order.rollback("INVENTORY"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot rollback order in state: " + status);
        }

        @Test
        void reject_WhenStatusIsNew_ShouldSetStatusRejectedAndSetSource() {
            Order order = new Order().setStatus(OrderStatus.NEW);

            order.reject("INVENTORY");

            assertThat(order.getStatus()).isEqualTo(OrderStatus.REJECTED);
            assertThat(order.getSource()).isEqualTo("INVENTORY");
        }

        @ParameterizedTest
        @EnumSource(
                value = OrderStatus.class,
                names = {"CONFIRMED", "ROLLBACK", "REJECTED", "FAILED", "ACCEPT"})
        void reject_WhenStatusIsNotNew_ShouldThrowIllegalStateException(OrderStatus status) {
            Order order = new Order().setStatus(status);

            assertThatThrownBy(() -> order.reject("INVENTORY"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot reject order in state: " + status);
        }

        @Test
        void fail_WhenStatusIsNew_ShouldSetStatusFailedAndSetSource() {
            Order order = new Order().setStatus(OrderStatus.NEW);

            order.fail("SAGA_TIMEOUT");

            assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);
            assertThat(order.getSource()).isEqualTo("SAGA_TIMEOUT");
        }

        @ParameterizedTest
        @EnumSource(
                value = OrderStatus.class,
                names = {"CONFIRMED", "ROLLBACK", "REJECTED", "FAILED", "ACCEPT"})
        void fail_WhenStatusIsNotNew_ShouldThrowIllegalStateException(OrderStatus status) {
            Order order = new Order().setStatus(status);

            assertThatThrownBy(() -> order.fail("TIMEOUT"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot fail order in state: " + status);
        }

        @Test
        void isTerminal_ShouldReturnTrueForTerminalStatesAndFalseOtherwise() {
            assertThat(new Order().setStatus(OrderStatus.CONFIRMED).isTerminal()).isTrue();
            assertThat(new Order().setStatus(OrderStatus.REJECTED).isTerminal()).isTrue();
            assertThat(new Order().setStatus(OrderStatus.ROLLBACK).isTerminal()).isTrue();
            assertThat(new Order().setStatus(OrderStatus.FAILED).isTerminal()).isTrue();

            assertThat(new Order().setStatus(OrderStatus.NEW).isTerminal()).isFalse();
            assertThat(new Order().setStatus(OrderStatus.ACCEPT).isTerminal()).isFalse();
        }

        @Test
        void transitionTo_ShouldDelegateCorrectly() {
            Order order1 = new Order().setStatus(OrderStatus.NEW);
            order1.transitionTo(OrderStatus.CONFIRMED, null);
            assertThat(order1.getStatus()).isEqualTo(OrderStatus.CONFIRMED);

            Order order2 = new Order().setStatus(OrderStatus.NEW);
            order2.transitionTo(OrderStatus.ROLLBACK, "PAYMENT");
            assertThat(order2.getStatus()).isEqualTo(OrderStatus.ROLLBACK);

            Order order3 = new Order().setStatus(OrderStatus.NEW);
            order3.transitionTo(OrderStatus.REJECTED, "INVENTORY");
            assertThat(order3.getStatus()).isEqualTo(OrderStatus.REJECTED);

            Order order4 = new Order().setStatus(OrderStatus.NEW);
            order4.transitionTo(OrderStatus.FAILED, "TIMEOUT");
            assertThat(order4.getStatus()).isEqualTo(OrderStatus.FAILED);
        }

        @Test
        void transitionTo_WhenUnsupportedStatus_ShouldThrowIllegalArgumentException() {
            Order order = new Order().setStatus(OrderStatus.NEW);

            assertThatThrownBy(() -> order.transitionTo(OrderStatus.NEW, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unsupported target transition status");
        }
    }

    @Nested
    @DisplayName("Domain Calculations and Item Relationships")
    class CalculationsAndItems {

        @Test
        void getTotalAmount_ShouldCalculateSumOfAllOrderItemSubtotals() {
            Order order = new Order();
            OrderItem item1 =
                    new OrderItem()
                            .setProductCode("P1")
                            .setQuantity(2)
                            .setProductPrice(new BigDecimal("10.50"));
            OrderItem item2 =
                    new OrderItem()
                            .setProductCode("P2")
                            .setQuantity(3)
                            .setProductPrice(new BigDecimal("5.00"));

            order.addOrderItem(item1);
            order.addOrderItem(item2);

            // 2 * 10.50 = 21.00; 3 * 5.00 = 15.00; sum = 36.00
            assertThat(order.getTotalAmount()).isEqualByComparingTo(new BigDecimal("36.00"));
        }

        @Test
        void getTotalAmount_WhenNoItems_ShouldReturnZero() {
            Order order = new Order();

            assertThat(order.getTotalAmount()).isEqualTo(BigDecimal.ZERO);
        }

        @Test
        void getSubTotal_WhenProductPriceIsNull_ShouldReturnZero() {
            OrderItem item = new OrderItem().setQuantity(5);

            assertThat(item.getSubTotal()).isEqualTo(BigDecimal.ZERO);
        }

        @Test
        void addAndRemoveOrderItem_ShouldMaintainBidirectionalRelationship() {
            Order order = new Order();
            OrderItem item = new OrderItem().setProductCode("P1");

            order.addOrderItem(item);
            assertThat(order.getItems()).contains(item);
            assertThat(item.getOrder()).isEqualTo(order);

            order.removeOrderItem(item);
            assertThat(order.getItems()).doesNotContain(item);
            assertThat(item.getOrder()).isNull();
        }
    }
}
