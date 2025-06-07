package com.example.retailstore.webapp.web.controller;

import com.example.retailstore.webapp.services.KeycloakRegistrationService;
import com.example.retailstore.webapp.web.model.request.RegistrationRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
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

    private static final Logger logger = LoggerFactory.getLogger(RegistrationController.class);

    private final KeycloakRegistrationService registrationService;

    public RegistrationController(KeycloakRegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegistrationRequest request) {
        try {
            registrationService.registerUser(request);
            return ResponseEntity.ok(Map.of("message", "User registered successfully"));
        } catch (RuntimeException e) {
            logger.error("Registration failed: {}", e.getMessage(), e);
            ProblemDetail problemDetail =
                    ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid request content.");
            problemDetail.setType(URI.create("about:blank"));
            problemDetail.setTitle("Bad Request");
            problemDetail.setStatus(400);
            problemDetail.setInstance(URI.create("/api/register"));
            return ResponseEntity.badRequest().body(problemDetail);
        } catch (Exception e) {
            logger.error("Unexpected error during registration: {}", e.getMessage(), e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred. Please try again later.");
            problemDetail.setType(URI.create("about:blank"));
            problemDetail.setTitle("Internal Server Error");
            problemDetail.setStatus(500);
            problemDetail.setInstance(URI.create("/api/register"));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problemDetail);
        }
    }
}
