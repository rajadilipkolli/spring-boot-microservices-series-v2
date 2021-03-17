package com.example.api.gateway.configuration;

import com.example.api.gateway.domain.UsernameDTO;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.ReactiveAuditorAware;
import org.springframework.data.mongodb.config.EnableReactiveMongoAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.userdetails.UserDetails;
import reactor.core.publisher.Mono;

@Configuration
@EnableReactiveMongoAuditing
public class MongoConfig {

    @Bean
    ReactiveAuditorAware<UsernameDTO> reactiveAuditorAware() {
        return () ->
                ReactiveSecurityContextHolder.getContext()
                        .map(SecurityContext::getAuthentication)
                        .filter(Authentication::isAuthenticated)
                        .map(Authentication::getPrincipal)
                        .map(UserDetails.class::cast)
                        .map(UserDetails::getUsername)
                        .map(UsernameDTO::new)
                        .switchIfEmpty(Mono.empty());
    }
}
