package com.example.retailstore.webapp.web.controller;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LoginController {

    @GetMapping("/login")
    public String login(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()) {
            return "redirect:/";
        }
        return "login";
    }

    @GetMapping("/inventory")
    public String inventory() {
        return "inventory";
    }
}
