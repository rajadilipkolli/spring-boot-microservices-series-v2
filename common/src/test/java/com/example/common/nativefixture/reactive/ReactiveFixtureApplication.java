/***
 * <p>
 * Licensed under MIT License Copyright (c) 2026 Raja Kolli.
 * </p>
 ***/

package com.example.common.nativefixture.reactive;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.context.annotation.ImportRuntimeHints;

@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
@ImportRuntimeHints(ReactiveFixtureHints.class)
public class ReactiveFixtureApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReactiveFixtureApplication.class, args);
    }
}
