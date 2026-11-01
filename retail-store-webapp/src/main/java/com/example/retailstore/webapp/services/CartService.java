/***
 * <p>
 * Licensed under MIT License Copyright (c) 2026 Raja Kolli.
 * </p>
 ***/
package com.example.retailstore.webapp.services;

import com.example.retailstore.webapp.model.CartState;
import jakarta.servlet.http.HttpSession;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class CartService {

    public static final String CART_SESSION_KEY = "CART_SESSION_STATE";

    /** Returns the session cart, creating and storing an empty cart with a new revision if absent. */
    public CartState getCart(HttpSession session) {
        CartState cart = (CartState) session.getAttribute(CART_SESSION_KEY);
        if (cart == null) {
            cart = new CartState(
                    new ArrayList<>(), BigDecimal.ZERO, UUID.randomUUID().toString());
            session.setAttribute(CART_SESSION_KEY, cart);
        }
        return cart;
    }

    /**
     * Stores the supplied items and total with a new revision. If a cart exists and the supplied
     * revision is non-null and differs from its revision, leaves the session unchanged.
     *
     * @return the stored cart, or an empty optional for a revision mismatch
     */
    public Optional<CartState> updateCart(CartState cart, HttpSession session) {
        CartState existingCart = (CartState) session.getAttribute(CART_SESSION_KEY);
        if (existingCart != null
                && cart.revision() != null
                && !existingCart.revision().equals(cart.revision())) {
            return Optional.empty();
        }
        CartState updatedCart = new CartState(
                cart.items(), cart.totalAmount(), UUID.randomUUID().toString());
        session.setAttribute(CART_SESSION_KEY, updatedCart);
        return Optional.of(updatedCart);
    }

    /**
     * Removes the session cart unless a non-null revision conflicts with an existing cart.
     *
     * @param revision revision to check, or null to delete without checking
     * @return false for a revision mismatch; true otherwise, including when no cart exists
     */
    public boolean deleteCart(String revision, HttpSession session) {
        CartState existingCart = (CartState) session.getAttribute(CART_SESSION_KEY);
        if (existingCart != null && revision != null && !existingCart.revision().equals(revision)) {
            return false;
        }
        session.removeAttribute(CART_SESSION_KEY);
        return true;
    }
}
