/* Licensed under Apache-2.0 2021 */
package com.example.api.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@ConfigurationPropertiesScan
// This is the hack to fetch the MutiOpenGroupAPI
@SpringBootApplication(scanBasePackages = {"org.springdoc.webflux.api", "com.example.api.gateway"})
public class APIGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(APIGatewayApplication.class, args);
    }
}
