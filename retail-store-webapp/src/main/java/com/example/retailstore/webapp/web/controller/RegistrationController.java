package com.example.retailstore.webapp.web.controller;

import com.example.retailstore.webapp.services.KeycloakRegistrationService;
import com.example.retailstore.webapp.web.model.request.RegistrationRequest;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Validated
public class RegistrationController {

    private final KeycloakRegistrationService registrationService;

    public RegistrationController(KeycloakRegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegistrationRequest request) {
        try {
            registrationService.registerUser(request);
            return ResponseEntity.ok(Map.of("message", "User registered successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
