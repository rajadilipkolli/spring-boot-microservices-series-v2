/* (C)2022 */
package com.example.inventoryservice.web.controllers;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.inventoryservice.common.AbstractIntegrationTest;
import com.example.inventoryservice.dtos.InventoryDto;
import com.example.inventoryservice.entities.Inventory;
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
        inventoryList.add(new Inventory(1L, "First Inventory", 5, 0));
        inventoryList.add(new Inventory(2L, "Second Inventory", 6, 0));
        inventoryList.add(new Inventory(3L, "Third Inventory", 7, 0));
        inventoryList = inventoryRepository.saveAll(inventoryList);
    }

    @Test
    void shouldFetchAllInventorys() throws Exception {
        this.mockMvc
                .perform(get("/api/inventory"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()", is(inventoryList.size())));
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
    void shouldCreateNewInventory() throws Exception {
        InventoryDto inventory = new InventoryDto("New Inventory", 10);
        this.mockMvc
                .perform(
                        post("/api/inventory")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(inventory)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.productCode", is(inventory.getProductCode())));
    }

    @Test
    void shouldReturn400WhenCreateNewInventoryWithoutProductCode() throws Exception {
        InventoryDto inventory = new InventoryDto(null, 0);

        this.mockMvc
                .perform(
                        post("/api/inventory")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(inventory)))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", is("application/problem+json")))
                .andExpect(
                        jsonPath(
                                "$.type",
                                is("https://zalando.github.io/problem/constraint-violation")))
                .andExpect(jsonPath("$.title", is("Constraint Violation")))
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.violations", hasSize(1)))
                .andExpect(jsonPath("$.violations[0].field", is("productCode")))
                .andExpect(jsonPath("$.violations[0].message", is("ProductCode can't be blank")))
                .andReturn();
    }

    @Test
    void shouldUpdateInventory() throws Exception {
        Inventory inventory = inventoryList.get(0);
        inventory.setProductCode("Updated Inventory");

        this.mockMvc
                .perform(
                        put("/api/inventory/{id}", inventory.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(inventory)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productCode", is(inventory.getProductCode())));
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
