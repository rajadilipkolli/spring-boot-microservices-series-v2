package com.example.api.gateway.web.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.api.gateway.service.GenerationService;
import com.example.api.gateway.service.exception.TemplateNotFoundException;

@WebMvcTest(GenerateController.class)
class GenerateControllerTest {

    @MockBean
    private GenerationService generationService;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldReturnGeneratedValueWhenTemplateIsValid() throws Exception {
        // Arrange
        String template = "hello";
        String expected = "world";
        when(generationService.generate(template)).thenReturn(expected);

        // Act & Assert
        mockMvc.perform(get("/generate").param("template", template))
            .andExpect(status().isOk())
            .andExpect(content().string(expected));
    }

    @Test
    void shouldReturnBadRequestWhenTemplateParameterIsMissing() throws Exception {
        mockMvc.perform(get("/generate"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnBadRequestWhenTemplateIsBlank() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/generate").param("template", ""))
            .andExpect(status().isBadRequest())
            .andExpect(content().string("Template must not be blank"));
    }

    @Test
    void shouldReturnNotFoundWhenTemplateDoesNotExist() throws Exception {
        // Arrange
        String template = "nonexistent";
        when(generationService.generate(template))
            .thenThrow(new TemplateNotFoundException("not found"));

        // Act & Assert
        mockMvc.perform(get("/generate").param("template", template))
            .andExpect(status().isNotFound())
            .andExpect(content().string("not found"));
    }

    @Test
    void shouldReturnInternalServerErrorWhenServiceFails() throws Exception {
        // Arrange
        when(generationService.generate(anyString()))
            .thenThrow(new RuntimeException("failure"));

        // Act & Assert
        mockMvc.perform(get("/generate").param("template", "anything"))
            .andExpect(status().isInternalServerError())
            .andExpect(content().string("Internal server error"));
    }
}