/***
<p>
    Licensed under MIT License Copyright (c) 2021-2024 Raja Kolli.
</p>
***/

package com.example.inventoryservice.web.controllers;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.instancio.Select.field;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.inventoryservice.common.AbstractIntegrationTest;
import com.example.inventoryservice.entities.Inventory;
import com.example.inventoryservice.model.request.InventoryRequest;
import com.example.inventoryservice.repositories.InventoryRepository;
import java.util.List;
import org.instancio.Instancio;
import org.instancio.junit.InstancioExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

@ExtendWith(InstancioExtension.class)
class InventoryControllerIT extends AbstractIntegrationTest {

    @Autowired private InventoryRepository inventoryRepository;

    private List<Inventory> inventoryList = null;

    @BeforeEach
    void setUp() {
        inventoryRepository.deleteAllInBatch();

        List<Inventory> inventories =
                Instancio.ofList(Inventory.class)
                        .size(15)
                        .ignore(field(Inventory::getId))
                        .set(field(Inventory::getVersion), (short) 0)
                        .create();
        inventoryList = inventoryRepository.saveAll(inventories);
    }

    @Test
    void shouldFetchAllInventories() throws Exception {
        this.mockMvc
                .perform(get("/api/inventory"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()", is(8)))
                .andExpect(jsonPath("$.data.size()", is(10)))
                .andExpect(jsonPath("$.totalElements", is(15)))
                .andExpect(jsonPath("$.pageNumber", is(1)))
                .andExpect(jsonPath("$.totalPages", is(2)))
                .andExpect(jsonPath("$.isFirst", is(true)))
                .andExpect(jsonPath("$.isLast", is(false)))
                .andExpect(jsonPath("$.hasNext", is(true)))
                .andExpect(jsonPath("$.hasPrevious", is(false)));
    }

    @Test
    void shouldFindInventoryByProductCode() throws Exception {
        Inventory inventory = inventoryList.getFirst();
        String productCode = inventory.getProductCode();

        this.mockMvc
                .perform(get("/api/inventory/{productCode}", productCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productCode", is(inventory.getProductCode())))
                .andExpect(jsonPath("$.availableQuantity").value(inventory.getAvailableQuantity()))
                .andExpect(jsonPath("$.reservedItems").value(inventory.getReservedItems()));
    }

    @Test
    void shouldFindInventoriesByProductCodes() throws Exception {
        String[] productCodeList =
                inventoryList.stream().map(Inventory::getProductCode).toArray(String[]::new);

        this.mockMvc
                .perform(get("/api/inventory/product").param("codes", productCodeList))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()", is(inventoryList.size())))
                .andExpect(
                        jsonPath("$[0].productCode")
                                .value(inventoryList.getFirst().getProductCode()))
                .andExpect(
                        jsonPath("$[0].availableQuantity")
                                .value(inventoryList.getFirst().getAvailableQuantity()))
                .andExpect(
                        jsonPath("$[0].reservedItems")
                                .value(inventoryList.getFirst().getReservedItems()))
                .andExpect(
                        jsonPath("$[1].productCode").value(inventoryList.get(1).getProductCode()))
                .andExpect(
                        jsonPath("$[1].availableQuantity")
                                .value(inventoryList.get(1).getAvailableQuantity()))
                .andExpect(
                        jsonPath("$[1].reservedItems")
                                .value(inventoryList.get(1).getReservedItems()));
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
                .andExpect(jsonPath("$.reservedItems", is(0)))
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
                .andExpect(jsonPath("$.title", is("Constraint Violation")))
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.detail", is("Invalid request content.")))
                .andExpect(jsonPath("$.instance", is("/api/inventory")))
                .andExpect(jsonPath("$.violations").isArray())
                .andExpect(jsonPath("$.violations[0].field").exists())
                .andExpect(jsonPath("$.violations[0].message").exists())
                .andReturn();
    }

    @Test
    void shouldUpdateInventory() throws Exception {
        Inventory inventory = inventoryList.getFirst();
        Integer availableQuantity = inventory.getAvailableQuantity();
        InventoryRequest inventoryRequest =
                new InventoryRequest(inventory.getProductCode(), availableQuantity + 1000);

        this.mockMvc
                .perform(
                        put("/api/inventory/{id}", inventory.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(inventoryRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productCode", is(inventory.getProductCode())))
                .andExpect(jsonPath("$.availableQuantity", is(availableQuantity + 1000)))
                .andExpect(jsonPath("$.reservedItems").value(inventory.getReservedItems()));
    }

    @Test
    void shouldDeleteInventory() throws Exception {
        Inventory inventory = inventoryList.getFirst();

        this.mockMvc
                .perform(delete("/api/inventory/{id}", inventory.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productCode", is(inventory.getProductCode())))
                .andExpect(jsonPath("$.availableQuantity", is(inventory.getAvailableQuantity())))
                .andExpect(jsonPath("$.reservedItems").value(inventory.getReservedItems()));
    }
}
