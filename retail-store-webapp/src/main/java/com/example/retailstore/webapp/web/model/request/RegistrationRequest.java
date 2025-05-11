package com.example.retailstore.webapp.web.model.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record RegistrationRequest(
        @NotBlank String username,
        @NotBlank @Email String email,
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotBlank String password) {}
