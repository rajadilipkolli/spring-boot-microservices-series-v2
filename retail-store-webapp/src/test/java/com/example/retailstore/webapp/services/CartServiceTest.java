/***
 * <p>
 * Licensed under MIT License Copyright (c) 2026 Raja Kolli.
 * </p>
 ***/
package com.example.retailstore.webapp.services;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.retailstore.webapp.model.CartItem;
import com.example.retailstore.webapp.model.CartState;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

class CartServiceTest {

    private CartService cartService;
    private MockHttpSession session;

    @BeforeEach
    void setUp() {
        cartService = new CartService();
        session = new MockHttpSession();
    }

    @Test
    @DisplayName("Should initialize empty cart when no cart in session")
    void shouldInitializeEmptyCart() {
        CartState cart = cartService.getCart(session);

        assertThat(cart).isNotNull();
        assertThat(cart.items()).isEmpty();
        assertThat(cart.totalAmount()).isEqualTo(BigDecimal.ZERO);
        assertThat(cart.revision()).isNotBlank();
        assertThat(session.getAttribute(CartService.CART_SESSION_KEY)).isSameAs(cart);
    }

    @Test
    @DisplayName("Should return existing cart when present in session")
    void shouldReturnExistingCart() {
        CartState initial = cartService.getCart(session);
        CartState retrieved = cartService.getCart(session);

        assertThat(retrieved).isSameAs(initial);
    }

    @Test
    @DisplayName("Should update cart when revision matches")
    void shouldUpdateCartWhenRevisionMatches() {
        CartState initial = cartService.getCart(session);

        List<CartItem> items = List.of(new CartItem("P100", "Product 100", BigDecimal.valueOf(10.50), 2));
        CartState toUpdate = new CartState(items, BigDecimal.valueOf(21.00), initial.revision());

        Optional<CartState> updated = cartService.updateCart(toUpdate, session);

        assertThat(updated).isPresent();
        assertThat(updated.get().items()).hasSize(1);
        assertThat(updated.get().totalAmount()).isEqualTo(BigDecimal.valueOf(21.00));
        assertThat(updated.get().revision()).isNotEqualTo(initial.revision());
    }

    @Test
    @DisplayName("Should reject update when revision is stale")
    void shouldRejectUpdateWhenRevisionIsStale() {
        cartService.getCart(session);

        CartState staleUpdate = new CartState(List.of(), BigDecimal.ZERO, "stale-revision-identifier");
        Optional<CartState> result = cartService.updateCart(staleUpdate, session);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should delete cart when revision matches")
    void shouldDeleteCartWhenRevisionMatches() {
        CartState initial = cartService.getCart(session);

        boolean deleted = cartService.deleteCart(initial.revision(), session);

        assertThat(deleted).isTrue();
        assertThat(session.getAttribute(CartService.CART_SESSION_KEY)).isNull();
    }

    @Test
    @DisplayName("Should reject delete when revision is stale")
    void shouldRejectDeleteWhenRevisionIsStale() {
        cartService.getCart(session);

        boolean deleted = cartService.deleteCart("wrong-revision", session);

        assertThat(deleted).isFalse();
        assertThat(session.getAttribute(CartService.CART_SESSION_KEY)).isNotNull();
    }
}
