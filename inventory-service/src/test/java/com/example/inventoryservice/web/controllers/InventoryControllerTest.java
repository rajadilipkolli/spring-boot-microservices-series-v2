/***
<p>
    Licensed under MIT License Copyright (c) 2021-2023 Raja Kolli.
</p>
***/

package com.example.inventoryservice.web.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.inventoryservice.entities.Inventory;
import com.example.inventoryservice.model.request.InventoryRequest;
import com.example.inventoryservice.model.response.PagedResult;
import com.example.inventoryservice.services.InventoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.instancio.Instancio;
import org.instancio.junit.InstancioExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = InventoryController.class)
@ActiveProfiles("test")
@ExtendWith(InstancioExtension.class)
class InventoryControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private InventoryService inventoryService;

    @Autowired private ObjectMapper objectMapper;

    private List<Inventory> inventoryList;

    @BeforeEach
    void setUp() {
        inventoryList = Instancio.ofList(Inventory.class).size(10).create();
    }

    @Test
    void shouldFetchAllInventories() throws Exception {
        Page<Inventory> page = new PageImpl<>(inventoryList);
        PagedResult<Inventory> inventoryPagedResult = new PagedResult<>(page);
        given(inventoryService.findAllInventories(0, 10, "id", "asc"))
                .willReturn(inventoryPagedResult);

        this.mockMvc
                .perform(get("/api/inventory"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()", is(8)))
                .andExpect(jsonPath("$.data.size()", is(inventoryList.size())))
                .andExpect(jsonPath("$.totalElements", is(10)))
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
        Inventory inventory =
                new Inventory()
                        .setId(1L)
                        .setProductCode("product1")
                        .setAvailableQuantity(10)
                        .setReservedItems(0);
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
        Inventory inventory =
                new Inventory()
                        .setId(1L)
                        .setProductCode("product1")
                        .setAvailableQuantity(10)
                        .setReservedItems(0);
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
                .andExpect(jsonPath("$.title", is("Constraint Violation")))
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.detail", is("Invalid request content.")))
                .andExpect(jsonPath("$.instance", is("/api/inventory")))
                .andReturn();
    }

    @Test
    void shouldReturn404WhenUpdatingNonExistingInventory() throws Exception {
        Long inventoryId = 1L;
        InventoryRequest inventoryRequest = new InventoryRequest("product1", 10);
        given(inventoryService.updateInventoryById(inventoryId, inventoryRequest))
                .willReturn(Optional.empty());

        this.mockMvc
                .perform(
                        put("/api/inventory/{id}", inventoryId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(inventoryRequest)))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldDeleteInventory() throws Exception {
        Long inventoryId = 1L;
        Inventory inventory =
                new Inventory()
                        .setId(1L)
                        .setProductCode("product1")
                        .setAvailableQuantity(10)
                        .setReservedItems(0);
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

    @Test
    void testUpdateInventoryWithRandomValue() throws Exception {
        // Setup
        doNothing().when(inventoryService).updateGeneratedInventory();

        // Execute & Verify
        mockMvc.perform(get("/api/inventory/generate"))
                .andExpect(status().isOk())
                .andExpect(
                        result ->
                                assertThat(
                                                Boolean.parseBoolean(
                                                        result.getResponse().getContentAsString()))
                                        .isTrue());

        // Verify interactions
        verify(inventoryService, times(1)).updateGeneratedInventory();
    }
}
