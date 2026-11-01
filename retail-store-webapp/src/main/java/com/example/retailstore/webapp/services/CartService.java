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

    public CartState getCart(HttpSession session) {
        CartState cart = (CartState) session.getAttribute(CART_SESSION_KEY);
        if (cart == null) {
            cart = new CartState(
                    new ArrayList<>(), BigDecimal.ZERO, UUID.randomUUID().toString());
            session.setAttribute(CART_SESSION_KEY, cart);
        }
        return cart;
    }

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

    public boolean deleteCart(String revision, HttpSession session) {
        CartState existingCart = (CartState) session.getAttribute(CART_SESSION_KEY);
        if (existingCart != null && revision != null && !existingCart.revision().equals(revision)) {
            return false;
        }
        session.removeAttribute(CART_SESSION_KEY);
        return true;
    }
}
