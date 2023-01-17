package com.example.catalogservice;

import com.example.catalogservice.config.ApplicationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({ApplicationProperties.class})
public class CatalogServiceApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(CatalogServiceApplication.class);
        application.setWebApplicationType(WebApplicationType.SERVLET);
        application.run(args);
    }
}
