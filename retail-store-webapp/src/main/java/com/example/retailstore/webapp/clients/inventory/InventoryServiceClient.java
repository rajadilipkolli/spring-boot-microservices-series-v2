package com.example.retailstore.webapp.clients.inventory;

import com.example.retailstore.webapp.clients.PagedResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PutExchange;

@HttpExchange("/inventory-service")
public interface InventoryServiceClient {

    @GetExchange("/api/inventory")
    PagedResult<InventoryResponse> getInventories(@RequestParam int pageNo);

    @PutExchange("/api/inventory/{id}")
    InventoryResponse updateInventory(
            @PathVariable Long id, @RequestBody InventoryUpdateRequest inventoryUpdateRequest);
}
