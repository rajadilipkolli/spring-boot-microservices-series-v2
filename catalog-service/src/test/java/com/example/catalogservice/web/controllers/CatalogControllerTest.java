package com.example.catalogservice.web.controllers;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.hasSize;
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

import com.example.catalogservice.entities.Catalog;
import com.example.catalogservice.services.CatalogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.zalando.problem.ProblemModule;
import org.zalando.problem.violations.ConstraintViolationProblemModule;

@WebMvcTest(controllers = CatalogController.class)
@ActiveProfiles("test")
class CatalogControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private CatalogService catalogService;

    @Autowired private ObjectMapper objectMapper;

    private List<Catalog> catalogList;

    @BeforeEach
    void setUp() {
        this.catalogList = new ArrayList<>();
        this.catalogList.add(new Catalog(1L, "text 1"));
        this.catalogList.add(new Catalog(2L, "text 2"));
        this.catalogList.add(new Catalog(3L, "text 3"));

        objectMapper.registerModule(new ProblemModule());
        objectMapper.registerModule(new ConstraintViolationProblemModule());
    }

    @Test
    void shouldFetchAllCatalogs() throws Exception {
        given(catalogService.findAllCatalogs()).willReturn(this.catalogList);

        this.mockMvc
                .perform(get("/api/catalog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()", is(catalogList.size())));
    }

    @Test
    void shouldFindCatalogById() throws Exception {
        Long catalogId = 1L;
        Catalog catalog = new Catalog(catalogId, "text 1");
        given(catalogService.findCatalogById(catalogId)).willReturn(Optional.of(catalog));

        this.mockMvc
                .perform(get("/api/catalog/{id}", catalogId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text", is(catalog.getText())));
    }

    @Test
    void shouldReturn404WhenFetchingNonExistingCatalog() throws Exception {
        Long catalogId = 1L;
        given(catalogService.findCatalogById(catalogId)).willReturn(Optional.empty());

        this.mockMvc.perform(get("/api/catalog/{id}", catalogId)).andExpect(status().isNotFound());
    }

    @Test
    void shouldCreateNewCatalog() throws Exception {
        given(catalogService.saveCatalog(any(Catalog.class)))
                .willAnswer((invocation) -> invocation.getArgument(0));

        Catalog catalog = new Catalog(1L, "some text");
        this.mockMvc
                .perform(
                        post("/api/catalog")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(catalog)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
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
        Long catalogId = 1L;
        Catalog catalog = new Catalog(catalogId, "Updated text");
        given(catalogService.findCatalogById(catalogId)).willReturn(Optional.of(catalog));
        given(catalogService.saveCatalog(any(Catalog.class)))
                .willAnswer((invocation) -> invocation.getArgument(0));

        this.mockMvc
                .perform(
                        put("/api/catalog/{id}", catalog.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(catalog)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text", is(catalog.getText())));
    }

    @Test
    void shouldReturn404WhenUpdatingNonExistingCatalog() throws Exception {
        Long catalogId = 1L;
        given(catalogService.findCatalogById(catalogId)).willReturn(Optional.empty());
        Catalog catalog = new Catalog(catalogId, "Updated text");

        this.mockMvc
                .perform(
                        put("/api/catalog/{id}", catalogId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(catalog)))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldDeleteCatalog() throws Exception {
        Long catalogId = 1L;
        Catalog catalog = new Catalog(catalogId, "Some text");
        given(catalogService.findCatalogById(catalogId)).willReturn(Optional.of(catalog));
        doNothing().when(catalogService).deleteCatalogById(catalog.getId());

        this.mockMvc
                .perform(delete("/api/catalog/{id}", catalog.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text", is(catalog.getText())));
    }

    @Test
    void shouldReturn404WhenDeletingNonExistingCatalog() throws Exception {
        Long catalogId = 1L;
        given(catalogService.findCatalogById(catalogId)).willReturn(Optional.empty());

        this.mockMvc
                .perform(delete("/api/catalog/{id}", catalogId))
                .andExpect(status().isNotFound());
    }
}
