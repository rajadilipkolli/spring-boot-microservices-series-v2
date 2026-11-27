/***
 * <p>
 * Licensed under MIT License Copyright (c) 2026 Raja Kolli.
 * </p>
 ***/
package com.example.retailstore.webapp.web.controller;

import com.example.retailstore.webapp.model.CartState;
import com.example.retailstore.webapp.services.CartService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cart")
@Validated
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    /**
     * Returns the session cart, storing an empty cart with a zero total and a new revision when
     * absent.
     */
    @GetMapping
    public CartState getCart(HttpSession session) {
        return cartService.getCart(session);
    }

    @PostMapping
    public ResponseEntity<?> updateCart(@RequestBody @Valid CartState cart, HttpSession session) {
        return cartService
                .updateCart(cart, session)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.CONFLICT).body("Stale cart data"));
    }

    @DeleteMapping
    public ResponseEntity<?> deleteCart(@RequestParam(required = false) String revision, HttpSession session) {
        boolean deleted = cartService.deleteCart(revision, session);
        if (!deleted) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Stale cart data");
        }
        return ResponseEntity.noContent().build();
    }
}
