package com.example.retailstore.webapp.web.controller;

import com.example.retailstore.webapp.clients.PagedResult;
import com.example.retailstore.webapp.clients.inventory.InventoryResponse;
import com.example.retailstore.webapp.clients.inventory.InventoryServiceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
class InventoryController {

    private static final Logger log = LoggerFactory.getLogger(InventoryController.class);

    private final InventoryServiceClient inventoryServiceClient;

    public InventoryController(InventoryServiceClient inventoryServiceClient) {
        this.inventoryServiceClient = inventoryServiceClient;
    }

    /**
     * Serves the inventory page. Access is restricted to users with ADMIN role
     * through Spring Security configuration.
     *
     * @return the inventory view name
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/inventory")
    String showInventoriesPage(@RequestParam(defaultValue = "0") int page, Model model) {
        log.debug("Inventory page accessed with page number: {}", page);
        model.addAttribute("pageNo", page);
        return "inventory";
    }

    @GetMapping(value = "/api/inventory", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    PagedResult<InventoryResponse> inventories(@RequestParam(defaultValue = "0") int page, Model model) {
        log.info("Fetching inventories for page: {}", page);
        return inventoryServiceClient.getInventories(page);
    }

    @PutMapping("/inventory")
    @ResponseBody
    InventoryResponse updateInventory(@RequestBody InventoryResponse inventoryResponse) {
        log.debug("Input Received :{}", inventoryResponse);
        return inventoryServiceClient.updateInventory(
                inventoryResponse.id(), inventoryResponse.createInventoryUpdateRequest());
    }
}
