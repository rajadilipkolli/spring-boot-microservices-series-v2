package com.example.retailstore.webapp.web.controller;

import com.example.retailstore.webapp.model.CartState;
import jakarta.servlet.http.HttpSession;
import java.util.ArrayList;
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
            cart = new CartState(new ArrayList<>(), 0.0);
            session.setAttribute(CART_SESSION_KEY, cart);
        }
        return cart;
    }

    @PostMapping
    public ResponseEntity<Void> updateCart(@RequestBody CartState cart, HttpSession session) {
        session.setAttribute(CART_SESSION_KEY, cart);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteCart(HttpSession session) {
        session.removeAttribute(CART_SESSION_KEY);
        return ResponseEntity.ok().build();
    }
}
