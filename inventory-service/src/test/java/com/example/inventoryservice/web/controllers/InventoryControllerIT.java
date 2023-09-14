/***
<p>
    Licensed under MIT License Copyright (c) 2021-2023 Raja Kolli.
</p>
***/

package com.example.inventoryservice.web.controllers;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.inventoryservice.common.AbstractIntegrationTest;
import com.example.inventoryservice.entities.Inventory;
import com.example.inventoryservice.model.response.request.InventoryRequest;
import com.example.inventoryservice.repositories.InventoryRepository;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class InventoryControllerIT extends AbstractIntegrationTest {

    @Autowired private InventoryRepository inventoryRepository;

    private List<Inventory> inventoryList = null;

    @BeforeEach
    void setUp() {
        inventoryRepository.deleteAll();

        inventoryList = new ArrayList<>();
        inventoryList.add(new Inventory(null, "First Inventory", 5, 0));
        inventoryList.add(new Inventory(null, "Second Inventory", 6, 0));
        inventoryList.add(new Inventory(null, "Third Inventory", 7, 0));
        inventoryList = inventoryRepository.saveAll(inventoryList);
    }

    @Test
    void shouldFetchAllInventories() throws Exception {
        this.mockMvc
                .perform(get("/api/inventory"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()", is(8)))
                .andExpect(jsonPath("$.data.size()", is(inventoryList.size())))
                .andExpect(jsonPath("$.totalElements", is(3)))
                .andExpect(jsonPath("$.pageNumber", is(1)))
                .andExpect(jsonPath("$.totalPages", is(1)))
                .andExpect(jsonPath("$.isFirst", is(true)))
                .andExpect(jsonPath("$.isLast", is(true)))
                .andExpect(jsonPath("$.hasNext", is(false)))
                .andExpect(jsonPath("$.hasPrevious", is(false)));
    }

    @Test
    void shouldFindInventoryByProductCode() throws Exception {
        Inventory inventory = inventoryList.get(0);
        String productCode = inventory.getProductCode();

        this.mockMvc
                .perform(get("/api/inventory/{productCode}", productCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productCode", is(inventory.getProductCode())));
    }

    @Test
    void shouldFindInventoriesByProductCodes() throws Exception {
        String[] productCodeList =
                inventoryList.stream().map(Inventory::getProductCode).toArray(String[]::new);

        this.mockMvc
                .perform(get("/api/inventory/product").param("codes", productCodeList))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()", is(inventoryList.size())))
                .andExpect(jsonPath("$[0].productCode").value("First Inventory"))
                .andExpect(jsonPath("$[0].availableQuantity").value(5))
                .andExpect(jsonPath("$[0].reservedItems").value(0))
                .andExpect(jsonPath("$[1].productCode").value("Second Inventory"))
                .andExpect(jsonPath("$[1].availableQuantity").value(6))
                .andExpect(jsonPath("$[1].reservedItems").value(0))
                .andExpect(jsonPath("$[2].productCode").value("Third Inventory"))
                .andExpect(jsonPath("$[2].availableQuantity").value(7))
                .andExpect(jsonPath("$[2].reservedItems").value(0));
    }

    @Test
    void shouldCreateNewInventory() throws Exception {
        InventoryRequest inventoryRequest = new InventoryRequest("New Inventory", 10);
        this.mockMvc
                .perform(
                        post("/api/inventory")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(inventoryRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue(), Long.class))
                .andExpect(jsonPath("$.availableQuantity", is(10)))
                // .andExpect(jsonPath("$.reservedItems", is(0)))
                .andExpect(jsonPath("$.productCode", is(inventoryRequest.productCode())));
    }

    @Test
    void shouldReturn400WhenCreateNewInventoryWithoutProductCode() throws Exception {
        InventoryRequest inventoryRequest = new InventoryRequest(null, 0);

        this.mockMvc
                .perform(
                        post("/api/inventory")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(inventoryRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", is("application/problem+json")))
                .andExpect(jsonPath("$.type", is("about:blank")))
                .andExpect(jsonPath("$.title", is("Bad Request")))
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.detail", is("Invalid request content.")))
                .andExpect(jsonPath("$.instance", is("/api/inventory")))
                .andReturn();
    }

    @Test
    void shouldUpdateInventory() throws Exception {
        Inventory inventory = inventoryList.get(0);
        InventoryRequest inventoryRequest = new InventoryRequest(inventory.getProductCode(), 1000);

        this.mockMvc
                .perform(
                        put("/api/inventory/{id}", inventory.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(inventoryRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productCode", is(inventory.getProductCode())))
                .andExpect(jsonPath("$.availableQuantity", is(1000)));
    }

    @Test
    void shouldDeleteInventory() throws Exception {
        Inventory inventory = inventoryList.get(0);

        this.mockMvc
                .perform(delete("/api/inventory/{id}", inventory.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productCode", is(inventory.getProductCode())));
    }
}
