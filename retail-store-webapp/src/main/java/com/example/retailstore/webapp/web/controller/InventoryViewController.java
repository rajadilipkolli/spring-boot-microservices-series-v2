package com.example.retailstore.webapp.web.controller;

import com.example.common.logging.LogSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
class InventoryViewController {

    private static final Logger log = LoggerFactory.getLogger(InventoryViewController.class);

    /**
     * Serves the inventory page. Access is restricted to users with ADMIN role
     * through Spring Security configuration.
     *
     * @return the inventory view name
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/inventory")
    String showInventoriesPage(@RequestParam(defaultValue = "0") int page, Model model) {
        log.debug("Inventory page accessed with page number: {}", LogSanitizer.sanitizeForLog(String.valueOf(page)));
        model.addAttribute("pageNo", page);
        return "inventory";
    }
}
