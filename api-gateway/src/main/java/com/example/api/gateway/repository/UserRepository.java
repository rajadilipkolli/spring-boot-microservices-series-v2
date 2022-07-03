/* Licensed under Apache-2.0 2021 */
package com.example.api.gateway.repository;

import com.example.api.gateway.domain.User;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Mono;

public interface UserRepository extends ReactiveMongoRepository<User, String> {

    Mono<User> findByUsername(String username);
}
