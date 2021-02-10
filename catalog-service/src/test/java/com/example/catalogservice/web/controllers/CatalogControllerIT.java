package com.example.catalogservice.web.controllers;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.catalogservice.common.AbstractIntegrationTest;
import com.example.catalogservice.entities.Catalog;
import com.example.catalogservice.repositories.CatalogRepository;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class CatalogControllerIT extends AbstractIntegrationTest {

    @Autowired private CatalogRepository catalogRepository;

    private List<Catalog> catalogList = null;

    @BeforeEach
    void setUp() {
        catalogRepository.deleteAll();

        catalogList = new ArrayList<>();
        catalogList.add(new Catalog(1L, "First Catalog"));
        catalogList.add(new Catalog(2L, "Second Catalog"));
        catalogList.add(new Catalog(3L, "Third Catalog"));
        catalogList = catalogRepository.saveAll(catalogList);
    }

    @Test
    void shouldFetchAllCatalogs() throws Exception {
        this.mockMvc
                .perform(get("/api/catalog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()", is(catalogList.size())));
    }

    @Test
    void shouldFindCatalogById() throws Exception {
        Catalog catalog = catalogList.get(0);
        Long catalogId = catalog.getId();

        this.mockMvc
                .perform(get("/api/catalog/{id}", catalogId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text", is(catalog.getText())));
    }

    @Test
    void shouldCreateNewCatalog() throws Exception {
        Catalog catalog = new Catalog(null, "New Catalog");
        this.mockMvc
                .perform(
                        post("/api/catalog")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(catalog)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.text", is(catalog.getText())));
    }

    @Test
    void shouldReturn400WhenCreateNewCatalogWithoutText() throws Exception {
        Catalog catalog = new Catalog(null, null);

        this.mockMvc
                .perform(
                        post("/api/catalog")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(catalog)))
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
    void shouldUpdateCatalog() throws Exception {
        Catalog catalog = catalogList.get(0);
        catalog.setText("Updated Catalog");

        this.mockMvc
                .perform(
                        put("/api/catalog/{id}", catalog.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(catalog)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text", is(catalog.getText())));
    }

    @Test
    void shouldDeleteCatalog() throws Exception {
        Catalog catalog = catalogList.get(0);

        this.mockMvc
                .perform(delete("/api/catalog/{id}", catalog.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text", is(catalog.getText())));
    }
}
