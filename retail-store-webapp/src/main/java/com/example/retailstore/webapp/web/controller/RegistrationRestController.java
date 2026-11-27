package com.example.retailstore.webapp.web.controller;

import com.example.retailstore.webapp.model.request.RegistrationRequest;
import com.example.retailstore.webapp.services.UserRegistrationService;
import jakarta.validation.Valid;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
public class RegistrationRestController {

    private static final Logger log = LoggerFactory.getLogger(RegistrationRestController.class);

    private final UserRegistrationService userRegistrationService;

    public RegistrationRestController(UserRegistrationService userRegistrationService) {
        this.userRegistrationService = userRegistrationService;
    }

    @PostMapping(value = "/api/register", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> register(@Valid @RequestBody RegistrationRequest request) {
        log.info("Received registration request for user: {}", request.username());
        userRegistrationService.registerUserAndCustomer(request);
        return ResponseEntity.ok(Map.of("message", "User registered successfully"));
    }
}
