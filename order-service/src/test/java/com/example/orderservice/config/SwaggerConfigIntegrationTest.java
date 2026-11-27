/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.orderservice.config;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.orderservice.common.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class SwaggerConfigIntegrationTest extends AbstractIntegrationTest {

    @Test
    void shouldServeOpenAPIEndpoint() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.info.title").value("order-service"))
                .andExpect(jsonPath("$.info.description").value("APIs related to Orders"))
                .andExpect(jsonPath("$.info.version").value("v1"))
                // Check 8 order operations
                .andExpect(jsonPath("$.paths['/api/orders'].get").exists())
                .andExpect(jsonPath("$.paths['/api/orders'].post").exists())
                .andExpect(jsonPath("$.paths['/api/orders/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/orders/{id}'].put").exists())
                .andExpect(jsonPath("$.paths['/api/orders/{id}'].delete").exists())
                .andExpect(jsonPath("$.paths['/api/orders/generate'].post").exists())
                .andExpect(jsonPath("$.paths['/api/orders/customer/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/orders/search'].get").exists())
                // Check paginated 200 schema resolves concrete element type OrderResponse
                .andExpect(jsonPath("$.components.schemas.OrderResponse").exists())
                .andExpect(
                        jsonPath(
                                        "$.components.schemas.PagedResultOrderResponse.properties.data.items.$ref")
                                .value(containsString("OrderResponse")))
                // Check order delete 202 has no content
                .andExpect(
                        jsonPath("$.paths['/api/orders/{id}'].delete.responses['202'].content")
                                .doesNotExist());
    }
}
