/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.inventoryservice.config;

import org.jooq.jpa.extensions.DefaultAnnotatedPojoMemberProvider;
import org.springframework.boot.jooq.autoconfigure.DefaultConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class JooqConfig {

    @Bean
    DefaultConfigurationCustomizer configurationCustomizer() {
        return (c) -> c.set(new DefaultAnnotatedPojoMemberProvider());
    }
}
