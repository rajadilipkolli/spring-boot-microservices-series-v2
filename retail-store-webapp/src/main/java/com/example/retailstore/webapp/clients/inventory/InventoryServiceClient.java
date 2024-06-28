package com.example.retailstore.webapp.clients.inventory;

import com.example.retailstore.webapp.clients.PagedResult;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;

public interface InventoryServiceClient {

    @GetExchange("/api/inventory")
    PagedResult<InventoryResponse> getInventories(@RequestParam int pageNo);
}
