/* Licensed under Apache-2.0 2021 */
package com.example.api.gateway.web;

import com.example.api.gateway.security.jwt.JwtTokenProvider;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtTokenProvider tokenProvider;

    private final ReactiveAuthenticationManager authenticationManager;

    @PostMapping("/login")
    public Mono<Object> login(@Valid @RequestBody Mono<AuthenticationRequest> authRequest) {

        return authRequest
                .flatMap(
                        login ->
                                this.authenticationManager
                                        .authenticate(
                                                new UsernamePasswordAuthenticationToken(
                                                        login.getUsername(), login.getPassword()))
                                        .map(this.tokenProvider::createToken))
                .map(
                        jwt -> {
                            HttpHeaders httpHeaders = new HttpHeaders();
                            httpHeaders.add(HttpHeaders.AUTHORIZATION, "Bearer " + jwt);
                            var tokenBody = Map.of("access_token", jwt);
                            return new ResponseEntity<>(tokenBody, httpHeaders, HttpStatus.OK);
                        });
    }
}
