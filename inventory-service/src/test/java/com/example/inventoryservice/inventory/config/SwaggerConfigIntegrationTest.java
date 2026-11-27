/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.inventoryservice.inventory.config;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.inventoryservice.inventory.web.service.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class SwaggerConfigIntegrationTest extends AbstractIntegrationTest {

    @Test
    void shouldServeOpenAPIEndpoint() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.info.title").value("inventory-service"))
                .andExpect(jsonPath("$.info.description").value("APIs related to Inventory"))
                .andExpect(jsonPath("$.info.version").value("v1"))
                // Check 8 inventory operations
                .andExpect(jsonPath("$.paths['/api/inventory'].get").exists())
                .andExpect(jsonPath("$.paths['/api/inventory'].post").exists())
                .andExpect(jsonPath("$.paths['/api/inventory/{productCode}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/inventory/{id}'].put").exists())
                .andExpect(jsonPath("$.paths['/api/inventory/product/{productCode}'].put").exists())
                .andExpect(jsonPath("$.paths['/api/inventory/{id}'].delete").exists())
                .andExpect(jsonPath("$.paths['/api/inventory/product'].get").exists())
                .andExpect(jsonPath("$.paths['/api/inventory/generate'].post").exists())
                // Check paginated 200 schema resolves concrete element type InventoryResponse
                .andExpect(jsonPath("$.components.schemas.InventoryResponse").exists())
                .andExpect(
                        jsonPath(
                                        "$.components.schemas.PagedResultInventoryResponse.properties.data.items.$ref")
                                .value(containsString("InventoryResponse")))
                // Check inventory 404 has no content
                .andExpect(
                        jsonPath(
                                        "$.paths['/api/inventory/{productCode}'].get.responses['404'].content")
                                .doesNotExist());
    }
}
