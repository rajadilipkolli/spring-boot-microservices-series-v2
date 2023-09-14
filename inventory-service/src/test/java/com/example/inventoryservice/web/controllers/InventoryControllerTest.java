/***
<p>
    Licensed under MIT License Copyright (c) 2021-2023 Raja Kolli.
</p>
***/

package com.example.inventoryservice.web.controllers;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.inventoryservice.entities.Inventory;
import com.example.inventoryservice.model.response.PagedResult;
import com.example.inventoryservice.model.response.request.InventoryRequest;
import com.example.inventoryservice.services.InventoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = InventoryController.class)
@ActiveProfiles("test")
class InventoryControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private InventoryService inventoryService;

    @Autowired private ObjectMapper objectMapper;

    private List<Inventory> inventoryList;

    @BeforeEach
    void setUp() {
        this.inventoryList = new ArrayList<>();
        this.inventoryList.add(new Inventory(1L, "text 1", 1, 0));
        this.inventoryList.add(new Inventory(2L, "text 2", 2, 0));
        this.inventoryList.add(new Inventory(3L, "text 3", 3, 0));
    }

    @Test
    void shouldFetchAllInventorys() throws Exception {
        Page<Inventory> page = new PageImpl<>(inventoryList);
        PagedResult<Inventory> inventoryPagedResult = new PagedResult<>(page);
        given(inventoryService.findAllInventories(0, 10, "id", "asc"))
                .willReturn(inventoryPagedResult);

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
        String productCode = "text 1";
        Inventory inventory = new Inventory(1L, "text 1", 1, 0);
        given(inventoryService.findInventoryByProductCode(productCode))
                .willReturn(Optional.of(inventory));

        this.mockMvc
                .perform(get("/api/inventory/{productCode}", productCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productCode", is(inventory.getProductCode())));
    }

    @Test
    void shouldReturn404WhenFetchingNonExistingInventory() throws Exception {
        Long inventoryId = 1L;
        given(inventoryService.findInventoryById(inventoryId)).willReturn(Optional.empty());

        this.mockMvc
                .perform(get("/api/inventory/{id}", inventoryId))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldCreateNewInventory() throws Exception {
        Inventory inventory = new Inventory(1L, "some text", 1, 0);
        given(inventoryService.saveInventory(any(InventoryRequest.class))).willReturn(inventory);

        InventoryRequest inventoryRequest = new InventoryRequest("some text", 1);
        this.mockMvc
                .perform(
                        post("/api/inventory")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(inventoryRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.productCode", is(inventory.getProductCode())));
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
        Long inventoryId = 1L;
        Inventory inventory = new Inventory(inventoryId, "Updated text", 30, 0);
        given(inventoryService.findInventoryById(inventoryId)).willReturn(Optional.of(inventory));
        InventoryRequest inventoryRequest = new InventoryRequest("Updated text", 30);
        given(inventoryService.updateInventory(inventory, inventoryRequest))
                .willAnswer((invocation) -> invocation.getArgument(0));

        this.mockMvc
                .perform(
                        put("/api/inventory/{id}", inventory.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(inventoryRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productCode", is(inventory.getProductCode())));
    }

    @Test
    void shouldReturn404WhenUpdatingNonExistingInventory() throws Exception {
        Long inventoryId = 1L;
        given(inventoryService.findInventoryById(inventoryId)).willReturn(Optional.empty());
        Inventory inventory = new Inventory(inventoryId, "Updated text", 8, 0);

        this.mockMvc
                .perform(
                        put("/api/inventory/{id}", inventoryId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(inventory)))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldDeleteInventory() throws Exception {
        Long inventoryId = 1L;
        Inventory inventory = new Inventory(inventoryId, "Some text", 5, 0);
        given(inventoryService.findInventoryById(inventoryId)).willReturn(Optional.of(inventory));
        doNothing().when(inventoryService).deleteInventoryById(inventory.getId());

        this.mockMvc
                .perform(delete("/api/inventory/{id}", inventory.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productCode", is(inventory.getProductCode())));
    }

    @Test
    void shouldReturn404WhenDeletingNonExistingInventory() throws Exception {
        Long inventoryId = 1L;
        given(inventoryService.findInventoryById(inventoryId)).willReturn(Optional.empty());

        this.mockMvc
                .perform(delete("/api/inventory/{id}", inventoryId))
                .andExpect(status().isNotFound());
    }
}
