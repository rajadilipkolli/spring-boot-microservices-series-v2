package com.example.api.gateway;

import com.example.api.gateway.domain.User;
import com.example.api.gateway.repository.UserRepository;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/** @author hantsy */
@Component
@Slf4j
@RequiredArgsConstructor
public class DataInitializer {

    private final UserRepository users;

    private final PasswordEncoder passwordEncoder;

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        log.info("start data initialization...");

        this.users
                .deleteAll()
                .thenMany(
                        Flux.just("user", "admin")
                                .flatMap(
                                        username -> {
                                            List<String> roles =
                                                    "user".equals(username)
                                                            ? Collections.singletonList("ROLE_USER")
                                                            : Arrays.asList(
                                                                    "ROLE_USER", "ROLE_ADMIN");

                                            User user =
                                                    User.builder()
                                                            .roles(roles)
                                                            .username(username)
                                                            .password(
                                                                    passwordEncoder.encode(
                                                                            "password"))
                                                            .email(username + "@example.com")
                                                            .build();

                                            return this.users.save(user);
                                        }))
                .subscribe(
                        data -> log.info("data:" + data),
                        err -> log.error("error:" + err),
                        () -> log.info("done initialization..."));
    }
}
