package com.example.retailstore.webapp.web.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LoginController {

    private static final Logger logger = LoggerFactory.getLogger(LoginController.class);

    @GetMapping("/login")
    public String login(Authentication authentication) {
        logger.debug("Login page requested");
        if (authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            logger.debug("User {} already authenticated, redirecting to home", authentication.getName());
            return "redirect:/";
        }
        return "login";
    }

    /**
     * Serves the inventory page. Access is restricted to users with ADMIN role
     * through Spring Security configuration.
     *
     * @return the inventory view name
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/inventory")
    public String inventory() {
        logger.debug("Inventory page accessed");
        return "inventory";
    }
}
