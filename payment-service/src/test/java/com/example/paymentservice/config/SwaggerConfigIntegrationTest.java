/*** Licensed under MIT License Copyright (c) 2026 Raja Kolli. ***/
package com.example.paymentservice.config;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.paymentservice.common.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class SwaggerConfigIntegrationTest extends AbstractIntegrationTest {

    @Test
    void shouldServeOpenAPIEndpoint() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.info.title").value("payment-service"))
                .andExpect(jsonPath("$.info.description").value("APIs related to Payment"))
                .andExpect(jsonPath("$.info.version").value("v1"))
                // Check 8 payment/customer operations
                .andExpect(jsonPath("$.paths['/api/customers'].get").exists())
                .andExpect(jsonPath("$.paths['/api/customers'].post").exists())
                .andExpect(jsonPath("$.paths['/api/customers/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/customers/{id}'].put").exists())
                .andExpect(jsonPath("$.paths['/api/customers/{id}'].delete").exists())
                .andExpect(jsonPath("$.paths['/api/customers/by-email'].get").exists())
                .andExpect(jsonPath("$.paths['/api/customers/{id}/orders'].get").exists())
                .andExpect(jsonPath("$.paths['/api/customers/name/{name}'].get").exists())
                // Check paginated 200 schema resolves concrete element types
                .andExpect(jsonPath("$.components.schemas.CustomerResponse").exists())
                .andExpect(
                        jsonPath(
                                        "$.paths['/api/customers'].get.responses['200'].content['application/json'].schema.$ref")
                                .value(containsString("PagedResultCustomerResponse")))
                .andExpect(
                        jsonPath(
                                        "$.paths['/api/customers/{id}/orders'].get.responses['200'].content['application/json'].schema.$ref")
                                .value(containsString("PagedResultOrderResponse")));
    }
}
