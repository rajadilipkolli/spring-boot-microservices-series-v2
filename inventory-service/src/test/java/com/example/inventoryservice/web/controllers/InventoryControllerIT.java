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
        inventoryList.add(new Inventory(1L, "First Inventory"));
        inventoryList.add(new Inventory(2L, "Second Inventory"));
        inventoryList.add(new Inventory(3L, "Third Inventory"));
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
    void shouldFindInventoryById() throws Exception {
        Inventory inventory = inventoryList.get(0);
        Long inventoryId = inventory.getId();

        this.mockMvc
                .perform(get("/api/inventory/{id}", inventoryId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text", is(inventory.getText())));
    }

    @Test
    void shouldCreateNewInventory() throws Exception {
        Inventory inventory = new Inventory(null, "New Inventory");
        this.mockMvc
                .perform(
                        post("/api/inventory")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(inventory)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.text", is(inventory.getText())));
    }

    @Test
    void shouldReturn400WhenCreateNewInventoryWithoutText() throws Exception {
        Inventory inventory = new Inventory(null, null);

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
                .andExpect(jsonPath("$.violations[0].field", is("text")))
                .andExpect(jsonPath("$.violations[0].message", is("Text cannot be empty")))
                .andReturn();
    }

    @Test
    void shouldUpdateInventory() throws Exception {
        Inventory inventory = inventoryList.get(0);
        inventory.setText("Updated Inventory");

        this.mockMvc
                .perform(
                        put("/api/inventory/{id}", inventory.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(inventory)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text", is(inventory.getText())));
    }

    @Test
    void shouldDeleteInventory() throws Exception {
        Inventory inventory = inventoryList.get(0);

        this.mockMvc
                .perform(delete("/api/inventory/{id}", inventory.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text", is(inventory.getText())));
    }
}
