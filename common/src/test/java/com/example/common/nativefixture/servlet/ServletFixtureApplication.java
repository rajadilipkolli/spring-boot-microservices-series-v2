/***
 * <p>
 * Licensed under MIT License Copyright (c) 2026 Raja Kolli.
 * </p>
 ***/

package com.example.common.nativefixture.servlet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ImportRuntimeHints;

@SpringBootApplication
@ImportRuntimeHints(ServletFixtureHints.class)
public class ServletFixtureApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServletFixtureApplication.class, args);
    }
}
