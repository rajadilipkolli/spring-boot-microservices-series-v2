/* Licensed under Apache-2.0 2021-2025 */
package com.example.configserver;

import com.example.configserver.config.ConfigServerRuntimeHints;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;
import org.springframework.context.annotation.ImportRuntimeHints;

@SpringBootApplication
@EnableConfigServer
@ImportRuntimeHints(ConfigServerRuntimeHints.class)
public class ConfigServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConfigServerApplication.class, args);
    }
}
