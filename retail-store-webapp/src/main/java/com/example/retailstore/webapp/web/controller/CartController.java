package com.example.retailstore.webapp.web.controller;

import com.example.retailstore.webapp.model.CartState;
import jakarta.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    private static final String CART_SESSION_KEY = "CART_SESSION_STATE";

    @GetMapping
    public CartState getCart(HttpSession session) {
        CartState cart = (CartState) session.getAttribute(CART_SESSION_KEY);
        if (cart == null) {
            cart = new CartState(new ArrayList<>(), 0.0, UUID.randomUUID().toString());
            session.setAttribute(CART_SESSION_KEY, cart);
        }
        return cart;
    }

    @PostMapping
    public ResponseEntity<?> updateCart(@RequestBody CartState cart, HttpSession session) {
        CartState existingCart = (CartState) session.getAttribute(CART_SESSION_KEY);
        if (existingCart != null
                && cart.revision() != null
                && !existingCart.revision().equals(cart.revision())) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.CONFLICT)
                    .body("Stale cart data");
        }
        CartState updatedCart = new CartState(
                cart.items(), cart.totalAmount(), java.util.UUID.randomUUID().toString());
        session.setAttribute(CART_SESSION_KEY, updatedCart);
        return ResponseEntity.ok(updatedCart);
    }

    @DeleteMapping
    public ResponseEntity<?> deleteCart(@RequestParam(required = false) String revision, HttpSession session) {
        CartState existingCart = (CartState) session.getAttribute(CART_SESSION_KEY);
        if (existingCart != null && revision != null && !existingCart.revision().equals(revision)) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.CONFLICT)
                    .body("Stale cart data");
        }
        session.removeAttribute(CART_SESSION_KEY);
        return ResponseEntity.ok().build();
    }
}
